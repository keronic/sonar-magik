package nl.ramsolutions.sw.magik.typedchecks.checks;

import com.sonar.sslr.api.AstNode;
import java.util.Map;
import nl.ramsolutions.sw.magik.analysis.definitions.DefSlottedExemplarParser;
import nl.ramsolutions.sw.magik.analysis.helpers.PackageNodeHelper;
import nl.ramsolutions.sw.magik.analysis.typing.ITypeKeeper;
import nl.ramsolutions.sw.magik.analysis.typing.TypeReader;
import nl.ramsolutions.sw.magik.analysis.typing.types.AbstractType;
import nl.ramsolutions.sw.magik.analysis.typing.types.TypeString;
import nl.ramsolutions.sw.magik.analysis.typing.types.UndefinedType;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.parser.TypeDocParser;
import nl.ramsolutions.sw.magik.typedchecks.MagikTypedCheck;

/**
 * Test if referenced type is known.
 */
public class TypeDocTypeExistsTypeCheck extends MagikTypedCheck {

    private static final String MESSAGE = "Unknown type: %s";

    @Override
    protected void walkPostMethodDefinition(final AstNode node) {
        this.checkMethodProcedureDefinition(node);
    }

    @Override
    protected void walkPostProcedureDefinition(final AstNode node) {
        this.checkMethodProcedureDefinition(node);
    }

    private void checkMethodProcedureDefinition(final AstNode node) {
        final TypeDocParser typeDocParser = new TypeDocParser(node);
        final PackageNodeHelper packageNodeHelper = new PackageNodeHelper(node);
        final String pakkage = packageNodeHelper.getCurrentPackage();
        this.checkDefinitionParameters(typeDocParser, pakkage);
        this.checkDefinitionLoops(typeDocParser, pakkage);
        this.checkDefinitionReturns(typeDocParser, pakkage);
    }

    private void checkDefinitionParameters(final TypeDocParser typeDocParser, final String pakkage) {
        // Test @param types.
        final ITypeKeeper typeKeeper = this.getTypeKeeper();
        final TypeReader typeParser = new TypeReader(typeKeeper);
        typeDocParser.getParameterTypeNodes().entrySet().stream()
            .filter(entry -> typeParser.parseTypeString(entry.getValue()) == UndefinedType.INSTANCE)
            .forEach(entry -> {
                final AstNode typeNode = entry.getKey();
                final TypeString typeString = entry.getValue();
                final String message = String.format(MESSAGE, typeString);
                this.addIssue(typeNode, message);
            });
    }

    private void checkDefinitionLoops(final TypeDocParser typeDocParser, final String pakkage) {
        // Test @loop types.
        final ITypeKeeper typeKeeper = this.getTypeKeeper();
        final TypeReader typeParser = new TypeReader(typeKeeper);
        typeDocParser.getLoopTypeNodes().entrySet().stream()
            .filter(entry -> typeParser.parseTypeString(entry.getValue()) == UndefinedType.INSTANCE)
            .forEach(entry -> {
                final AstNode typeNode = entry.getKey();
                final TypeString typeString = entry.getValue();
                final String message = String.format(MESSAGE, typeString);
                this.addIssue(typeNode, message);
            });
    }

    private void checkDefinitionReturns(final TypeDocParser typeDocParser, final String pakkage) {
        // Test @return types.
        final ITypeKeeper typeKeeper = this.getTypeKeeper();
        final TypeReader typeParser = new TypeReader(typeKeeper);
        typeDocParser.getReturnTypeNodes().entrySet().stream()
            .filter(entry -> typeParser.parseTypeString(entry.getValue()) == UndefinedType.INSTANCE)
            .forEach(entry -> {
                final AstNode typeNode = entry.getKey();
                final TypeString typeString = entry.getValue();
                final String message = String.format(MESSAGE, typeString);
                this.addIssue(typeNode, message);
            });
    }

    @Override
    protected void walkPostProcedureInvocation(final AstNode node) {
        if (!DefSlottedExemplarParser.isDefSlottedExemplar(node)) {
            return;
        }

        final ITypeKeeper typeKeeper = this.getTypeKeeper();
        final TypeReader typeParser = new TypeReader(typeKeeper);

        // Get slot defintions.
        final AstNode statementNode = node.getFirstAncestor(MagikGrammar.STATEMENT);
        final TypeDocParser typeDocParser = new TypeDocParser(statementNode);
        final Map<AstNode, TypeString> slotTypeNodes = typeDocParser.getSlotTypeNodes();

        // Test slot types.
        slotTypeNodes.entrySet().stream()
            .filter(entry -> {
                final AbstractType type = typeParser.parseTypeString(entry.getValue());
                return type == UndefinedType.INSTANCE;
            })
            .forEach(entry -> {
                final AstNode typeNode = entry.getKey();
                final TypeString typeString = entry.getValue();
                final String message = String.format(MESSAGE, typeString);
                this.addIssue(typeNode, message);
            });
    }

}