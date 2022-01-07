package nl.ramsolutions.sw.sonar.sensors;

import com.sonar.sslr.api.Token;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import nl.ramsolutions.sw.magik.MagikFile;
import nl.ramsolutions.sw.magik.MagikVisitor;
import nl.ramsolutions.sw.magik.checks.CheckList;
import nl.ramsolutions.sw.magik.checks.MagikCheck;
import nl.ramsolutions.sw.magik.checks.MagikIssue;
import nl.ramsolutions.sw.magik.metrics.FileMetrics;
import nl.ramsolutions.sw.sonar.TokenLocation;
import nl.ramsolutions.sw.sonar.language.Magik;
import nl.ramsolutions.sw.sonar.visitors.MagikHighlighterVisitor;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.squidbridge.ProgressReport;

/**
 * Magik squid Sensor.
 */
public class MagikSquidSensor implements Sensor {

    private static final Logger LOGGER = Loggers.get(MagikSquidSensor.class);
    private static final long SLEEP_PERIOD = 100;

    private final Checks<MagikCheck> checks;
    private final FileLinesContextFactory fileLinesContextFactory;
    private final NoSonarFilter noSonarFilter;

    /**
     * Constructor.
     * @param checkFactory Factory.
     * @param fileLinesContextFactory Factory.
     */
    public MagikSquidSensor(
            final CheckFactory checkFactory,
            final FileLinesContextFactory fileLinesContextFactory,
            final NoSonarFilter noSonarFilter) {
        this.checks = checkFactory
            .<MagikCheck>create(CheckList.REPOSITORY_KEY)
            .addAnnotatedChecks((Iterable<Class<?>>) CheckList.getChecks());
        this.fileLinesContextFactory = fileLinesContextFactory;
        this.noSonarFilter = noSonarFilter;
    }

    @Override
    public void describe(final SensorDescriptor descriptor) {
        descriptor
            .onlyOnLanguage(Magik.KEY)
            .name("Magik Squid Sensor")
            .onlyOnFileType(Type.MAIN);
    }

    @Override
    public void execute(final SensorContext context) {
        final FileSystem fileSystem = context.fileSystem();
        final FilePredicates predicates = fileSystem.predicates();

        final FilePredicate filePredicate = predicates.and(
            predicates.hasType(InputFile.Type.MAIN),
            predicates.hasLanguage(Magik.KEY));

        final List<InputFile> inputFiles = new ArrayList<>();
        fileSystem.inputFiles(filePredicate).forEach(inputFiles::add);

        final ProgressReport progressReport =
            new ProgressReport("Report about progress of Sonar Magik analyzer", SLEEP_PERIOD);
        final List<String> filenames = inputFiles.stream()
            .map(InputFile::toString)
            .collect(Collectors.toList());
        progressReport.start(filenames);

        for (final InputFile inputFile : inputFiles) {
            this.scanMagikFile(context, inputFile);
            progressReport.nextFile();
        }

        progressReport.stop();
    }

    private void scanMagikFile(final SensorContext context, final InputFile inputFile) {
        LOGGER.debug("Scanning magik file: {}", inputFile);

        // Read contents.
        final URI uri = inputFile.uri();
        final String fileContent;
        try {
            fileContent = inputFile.contents();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + inputFile, ex);
        }
        final MagikFile magikFile = new MagikFile(uri, fileContent);

        // Save metrics.
        LOGGER.debug("Save measures");
        this.saveMetrics(context, inputFile, magikFile);

        // Save issues.
        LOGGER.debug("Running checks");
        for (final MagikCheck check : this.checks.all()) {
            LOGGER.debug("Running check: {}", check);
            final List<MagikIssue> issues = check.scanFileForIssues(magikFile);
            this.saveIssues(context, check, issues, inputFile);
        }

        // Save highlighted tokens.
        LOGGER.debug("Saving highlighted tokens");
        final MagikVisitor tokensVisitor = new MagikHighlighterVisitor(context, inputFile);
        tokensVisitor.scanFile(magikFile);

        // Save CPD tokens.
        LOGGER.debug("Saving CPD tokens");
        this.saveCpdTokens(context, magikFile, inputFile);
    }

    private void saveMetrics(final SensorContext context, final InputFile inputFile, final MagikFile magikFile) {
        final FileMetrics metrics = new FileMetrics(magikFile, true);

        // Metrics on file.
        this.saveMetric(context, inputFile, CoreMetrics.NCLOC, metrics.linesOfCode().size());
        this.saveMetric(context, inputFile, CoreMetrics.COMMENT_LINES, metrics.commentLineCount());
        this.saveMetric(context, inputFile, CoreMetrics.CLASSES, metrics.numberOfExemplars());
        this.saveMetric(context, inputFile, CoreMetrics.FUNCTIONS,
            metrics.numberOfMethods()
            + metrics.numberOfProcedures());
        this.saveMetric(context, inputFile, CoreMetrics.STATEMENTS, metrics.numberOfStatements());
        this.saveMetric(context, inputFile, CoreMetrics.COMPLEXITY, metrics.fileComplexity());

        // Metrics on lines.
        final FileLinesContext fileLinesContext = this.fileLinesContextFactory.createFor(inputFile);
        metrics.linesOfCode().forEach(
            line -> fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1));
        metrics.executableLines().forEach(
            line -> fileLinesContext.setIntValue(CoreMetrics.EXECUTABLE_LINES_DATA_KEY, line, 1));
        fileLinesContext.save();

        // No sonar filter.
        this.noSonarFilter.noSonarInFile(inputFile, metrics.nosonarLines());
    }

    private void saveMetric(
            final SensorContext context,
            final InputFile inputFile,
            final Metric<Integer> metric,
            final Integer value) {
        LOGGER.debug("Saving metric, file: {}, metric: {} value: {}", inputFile, metric, value);

        context.<Integer>newMeasure()
            .withValue(value)
            .forMetric(metric)
            .on(inputFile)
            .save();
    }

    private void saveIssues(
            final SensorContext context,
            final MagikCheck check,
            final List<MagikIssue> issues,
            final InputFile inputFile) {
        for (final MagikIssue magikIssue : issues) {
            LOGGER.debug("Saving issue, file: {}, issue: {}", inputFile, magikIssue);

            final RuleKey ruleKey = this.checks.ruleKey(check);
            if (ruleKey == null) {
                continue;
            }
            final NewIssue issue = context.newIssue();
            final NewIssueLocation location = issue.newLocation()
                .on(inputFile)
                .message(magikIssue.message());
            final Integer line = magikIssue.startLine();
            if (line != null) {
                location.at(inputFile.selectLine(line));
            }
            issue.at(location).forRule(ruleKey).save();
        }
    }

    private void saveCpdTokens(final SensorContext context, final MagikFile magikFile, final InputFile inputFile) {
        LOGGER.debug("Saving CPD tokens, file: {}", inputFile);

        final NewCpdTokens newCpdTokens = context.newCpdTokens().onFile(inputFile);
        final List<Token> tokens = magikFile.getTopNode().getTokens();

        // For some reason, tokens sometimes get swapped.
        // We work around this by sorting the tokens.
        final Comparator<TokenLocation> byLine = Comparator.comparing(TokenLocation::line);
        final Comparator<TokenLocation> byColumn = Comparator.comparing(TokenLocation::column);

        tokens.stream()
            .filter(token -> !token.getValue().trim().equals(""))
            .map(TokenLocation::new)
            .sorted(byLine.thenComparing(byColumn))
            .forEach(tokenLocation -> newCpdTokens.addToken(
                tokenLocation.line(), tokenLocation.column(),
                tokenLocation.endLine(), tokenLocation.endColumn(),
                tokenLocation.getValue()));
        newCpdTokens.save();
    }
}