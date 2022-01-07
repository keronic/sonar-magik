package nl.ramsolutions.sw.magik.checks.checks;

import java.util.List;
import nl.ramsolutions.sw.magik.checks.MagikIssue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test VariableDeclarationUsageDistanceCheck.
 */
@SuppressWarnings("checkstyle:MagicNumber")
class VariableDeclarationUsageDistanceCheckTest extends MagikCheckTestBase {

    @Test
    void testDistanceOk() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 5;
        final String code = ""
            + "_method object.m\n"
            + "    _local a << 1\n"
            + "    do_something()\n"
            + "    do_something()\n"
            + "    write(a)\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).isEmpty();
    }

    @Test
    void testDistanceNotOk() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 2;
        final String code = ""
            + "_method object.m\n"
            + "    _local a << 1\n"
            + "    do_something()\n"
            + "    do_something()\n"
            + "    write(a)\n"
            + "    write(a)\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).hasSize(1);
    }

    @Test
    void testUpperScopeDistanceOk() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 5;
        final String code = ""
            + "_method object.m\n"
            + "    _local a << 1\n"
            + "    do_something()\n"
            + "    do_something()\n"
            + "    _if _true\n"
            + "    _then\n"
            + "        write(a)\n"
            + "    _endif\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).isEmpty();
    }

    @Test
    void testUpperScopeDistanceNotOk() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 2;
        final String code = ""
            + "_method object.m\n"
            + "    _local a << 1\n"
            + "    do_something()\n"
            + "    do_something()\n"
            + "    _if _true\n"
            + "    _then\n"
            + "        write(a)\n"
            + "    _endif\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).hasSize(1);
    }

    @Test
    void testDifferentScopesNotChecked() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 2;
        final String code = ""
            + "_method object.m\n"
            + "    _if _true\n"
            + "    _then\n"
            + "        a << 1\n"
            + "    _endif\n"
            + "    do_something()\n"
            + "    do_something()\n"
            + "    _if _true\n"
            + "    _then\n"
            + "        write(a)\n"
            + "    _endif\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).isEmpty();
    }

    @Test
    void testDistanceOkIndexerMethod() {
        final VariableDeclarationUsageDistanceCheck check = new VariableDeclarationUsageDistanceCheck();
        check.maxDistance = 2;
        final String code = ""
            + "_method object.m\n"
            + "    _local a << 1\n"
            + "    do_something()\n"
            + "    a[:abc] << :def\n"
            + "    a.do()\n"
            + "_endmethod";
        final List<MagikIssue> issues = this.runCheck(code, check);
        assertThat(issues).isEmpty();
    }

}