package com.splicemachine.derby.impl.sql.execute.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import com.splicemachine.derby.test.framework.SpliceDataWatcher;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;

/**
 * @author Jeff Cunningham
 *         Date: 2/11/15
 */
public class TruncateFunctionIT {

    private static SpliceWatcher spliceClassWatcher = new SpliceWatcher();

    private static SpliceSchemaWatcher schemaWatcher =
        new SpliceSchemaWatcher(TruncateFunctionIT.class.getSimpleName().toUpperCase());

    private static SpliceTableWatcher baseTableWatcher =
        new SpliceTableWatcher("trunctest",schemaWatcher.schemaName,"(s varchar(15), d date, t timestamp, n decimal (15, 7))");

    private static String[] TRUNCTEST_ROWS = {
        "'2012-05-23', '1988-12-26', '2000-06-07 17:12:30', 12345.6789",
    };

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
                                            .around(schemaWatcher)
                                            .around(baseTableWatcher)
                                           .around(new SpliceDataWatcher() {
                                               @Override
                                               protected void starting(Description description) {
                                                   PreparedStatement ps;
                                                   try {
                                                       for (String row : TRUNCTEST_ROWS) {
                                                           ps = spliceClassWatcher.prepareStatement(
                                                               String.format("insert into %s values (%s)",
                                                                             baseTableWatcher, row));
                                                           ps.execute();
                                                       }
                                                   } catch (Exception e) {
                                                       throw new RuntimeException(e);
                                                   }
                                               }
                                           });

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher();

    @Test
    public void testSelect() throws Exception {
        String sqlText =
            String.format("SELECT * from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
                "S     |     D     |          T           |      N       |\n" +
                "--------------------------------------------------------------\n" +
                "2012-05-23 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    //=========================================================================================================
    // Date column
    //=========================================================================================================

    @Test
    public void testTruncDateColumn_Year() throws Exception {
        String sqlText =
            String.format("select trunc(d, 'year') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd   |     D     |          T           |      N       |\n" +
                "--------------------------------------------------------------\n" +
                "1988-01-01 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateColumn_Month() throws Exception {
        String sqlText =
            String.format("select trunc(d, 'month') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd   |     D     |          T           |      N       |\n" +
                "--------------------------------------------------------------\n" +
                "1988-12-01 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateColumn_Day() throws Exception {
        String sqlText =
            String.format("select trunc(d, 'day') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd   |     D     |          T           |      N       |\n" +
                "--------------------------------------------------------------\n" +
                "1988-12-26 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateColumn_Default() throws Exception {
        // defaults to DAY
        String sqlText =
            String.format("select trunc(d) as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd   |     D     |          T           |      N       |\n" +
                "--------------------------------------------------------------\n" +
                "1988-12-26 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateColumn_Hour_invalid() throws Exception {
        String sqlText =
            String.format("select trunc(d, 'hour') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        try (ResultSet rs = methodWatcher.executeQuery(sqlText)) {
            fail("Expected exception.");
        } catch (Exception e) {
            Assert.assertEquals("The truncate function got an invalid right-side trunc value for operand type DATE: 'HOUR'.",
                                e.getLocalizedMessage());
        }
    }

    //=========================================================================================================
    // Values Date
    //=========================================================================================================

    @Test
    public void testTruncDateValues_Year() throws Exception {
        String sqlText = "values truncate(date('2011-12-26'), 'YEAR')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1     |\n" +
                "------------\n" +
                "2011-01-01 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateValues_Month() throws Exception {
        String sqlText = "values truncate(date('2011-12-26'), 'month')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1     |\n" +
                "------------\n" +
                "2011-12-01 |";
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateValues_Day() throws Exception {
        String sqlText = "values truncate(date('2011-12-26'), 'day')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1     |\n" +
                "------------\n" +
                "2011-12-26 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateValues_Default() throws Exception {
        // defauls to DAY
        String sqlText = "values truncate(date('2011-12-26'))";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1     |\n" +
                "------------\n" +
                "2011-12-26 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncDateValues_Hour_invalid() throws Exception {
        String sqlText = "values truncate(date('2011-12-26'), 'hour')";

        try (ResultSet rs = methodWatcher.executeQuery(sqlText)) {
            fail("Expected exception.");
        } catch (Exception e) {
            Assert.assertEquals("The truncate function got an invalid right-side trunc value for operand type DATE: 'HOUR'.",
                                e.getLocalizedMessage());
        }
    }

    //=========================================================================================================
    // Timestamp column
    //=========================================================================================================

    @Test
    public void testTruncTimstampColumn_Year() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'year') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-01-01 00:00:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumn_Month() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'month') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-01 00:00:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumn_Day() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'day') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-07 00:00:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumn_Default() throws Exception {
        // defaults to DAY
        String sqlText =
            String.format("select trunc(t) as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-07 00:00:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumn_Hour() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'hour') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-07 17:00:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumnMin() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'MINUTE') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-07 17:12:00.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampColumn_Min_invalid() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'minuite') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        try (ResultSet rs = methodWatcher.executeQuery(sqlText)) {
            fail("Expected exception.");
        } catch (Exception e) {
            Assert.assertEquals("The truncate function got an unknown right-side trunc value for operand type 'TIMESTAMP or DATE': 'MINUITE'. Acceptable values are: 'YEAR, YR, MONTH, MON, MO, DAY, HOUR, HR, MINUTE, MIN, SECOND, SEC, MILLISECOND, MILLI, NANOSECOND'.",
                                e.getLocalizedMessage());
        }
    }

    @Test
    public void testTruncTimestampColumn_Sec() throws Exception {
        String sqlText =
            String.format("select trunc(t, 'second') as \"truncd\", d, t, n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "truncd         |     D     |          T           |      N       |\n" +
                "-------------------------------------------------------------------------\n" +
                "2000-06-07 17:12:30.0 |1988-12-26 |2000-06-07 17:12:30.0 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    //=========================================================================================================
    // Values Timestamp
    //=========================================================================================================

    @Test
    public void testTruncTimestampValues_Year() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'YEAR')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-01-01 00:00:00.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Month() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'month')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-01 00:00:00.0 |";
        assertEquals("\n" + sqlText + "\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Default() throws Exception {
        // defaults to DAY
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'))";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-26 00:00:00.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Day() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'day')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-26 00:00:00.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Hour() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'hour')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-26 17:00:00.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Minute() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'minute')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-26 17:13:00.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testTruncTimestampValues_Second() throws Exception {
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'second')";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "2011-12-26 17:13:30.0 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    @Test
    public void testAlternativeTruncValues() throws Exception {
        // just making sure we don't get an exception using these alternative trunc value strings
        String sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'yr')";
        ResultSet rs = methodWatcher.executeQuery(sqlText);
        rs.close();

        sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'mon')";
        rs = methodWatcher.executeQuery(sqlText);
        rs.close();

        sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'mo')";
        rs = methodWatcher.executeQuery(sqlText);
        rs.close();

        sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'hr')";
        rs = methodWatcher.executeQuery(sqlText);
        rs.close();

        sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'min')";
        rs = methodWatcher.executeQuery(sqlText);
        rs.close();

        sqlText = "values truncate(timestamp('2011-12-26', '17:13:30'), 'sec')";
        rs = methodWatcher.executeQuery(sqlText);
        rs.close();
    }

    //=========================================================================================================
    // Column Decimal
    //=========================================================================================================

    @Test @Ignore("DB-1596 implementation")
    public void testTruncDecimalColumn() throws Exception {
        String sqlText =
            String.format("select trunc(n, 1), n from %s", baseTableWatcher.toString());

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |      N       |\n" +
                "-----------------------\n" +
                "12345.6 |12345.6789000 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

    //=========================================================================================================
    // Value Decimal
    //=========================================================================================================

    @Test @Ignore("DB-1596 implementation")
    public void testTruncDecimalValue() throws Exception {
        String sqlText =  "values truncate(12345.6789, 1)";

        ResultSet rs = methodWatcher.executeQuery(sqlText);

        String expected =
            "1           |\n" +
                "-----------------------\n" +
                "12345.6 |";
        assertEquals("\n"+sqlText+"\n", expected, TestUtils.FormattedResult.ResultFactory.toStringUnsorted(rs));
        rs.close();
    }

}