import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class HAReads {
    private static final String URL =
        "jdbc:postgresql://localhost:8812,localhost:8813,localhost:8814/qdb"
        + "?targetServerType=any&connectTimeout=3";

    private static final String USER = "admin";
    private static final String PASS = "quest";

    private Connection conn;  // persistent connection reference

    private Connection getConnectionWithRetry() {
        while (true) {
            try {
                Connection c = DriverManager.getConnection(URL, USER, PASS);
                c.setAutoCommit(true);
                System.out.println("Connected to " + c.getMetaData().getURL());
                return c;
            } catch (SQLException e) {
                System.err.println("Connection attempt failed: " + e.getMessage());
                try {
                    Thread.sleep(2000); // wait 2s before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry", ie);
                }
            }
        }
    }

    public String runQuery(String sql) throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = getConnectionWithRetry();
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            // on error, reconnect with retry loop and retry query
            System.err.println("Connection error, retrying: " + e.getMessage());
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignore) {}
            }
            conn = getConnectionWithRetry();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return null;
            }
        }
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignore) {}
            conn = null;
        }
    }

    public static void main(String[] args) throws Exception {
        HAReads demo = new HAReads();
        String query = "select value from (show parameters) "
                     + "where property_path = 'cairo.wal.temp.pending.rename.table.prefix';";

        for (int i = 1; i <= 250; i++) {
            String value = demo.runQuery(query);
            System.out.printf("Query %3d -> %s%n", i, value);
            Thread.sleep(300); // 300ms between queries
        }

        demo.close();
    }
}
