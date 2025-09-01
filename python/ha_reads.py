# ha_reads.py
import time
import psycopg
from psycopg import OperationalError, InterfaceError

class HAReads:
    CONN_STR = (
        "postgresql://admin:quest@"
        "localhost:8812,localhost:8813,localhost:8814/qdb"
        "?target_session_attrs=any&connect_timeout=3"
    )

    def __init__(self):
        self.conn: psycopg.Connection | None = None

    def get_connection_with_retry(self) -> psycopg.Connection:
        """Keep trying until a connection succeeds."""
        while True:
            try:
                conn = psycopg.connect(self.CONN_STR, autocommit=True)
                print(f"Connected to {conn.info.host}:{conn.info.port}")
                return conn
            except OperationalError as e:
                print(f"Connection attempt failed: {e}")
                time.sleep(2)

    def run_query(self, sql: str, params=None):
        """Run query, reconnect on connection error, retry once."""
        if self.conn is None or self.conn.closed:
            self.conn = self.get_connection_with_retry()

        try:
            with self.conn.cursor() as cur:
                cur.execute(sql, params)
                return cur.fetchall()
        except (OperationalError, InterfaceError) as e:
            print(f"Connection error, retrying: {e}")
            try:
                if self.conn and not self.conn.closed:
                    self.conn.close()
            except Exception:
                pass
            self.conn = self.get_connection_with_retry()
            with self.conn.cursor() as cur:
                cur.execute(sql, params)
                return cur.fetchall()

    def close(self):
        if self.conn and not self.conn.closed:
            try:
                self.conn.close()
            except Exception:
                pass
            self.conn = None

if __name__ == "__main__":
    demo = HAReads()
    query = (
        "select value from (show parameters) "
        "where property_path IN ( 'replication.role', 'cairo.wal.temp.pending.rename.table.prefix') limit 1;"
    )

    for i in range(1, 251):  # 250 iterations
        rows = demo.run_query(query)
        value = rows[0][0] if rows else None
        print(f"Query {i:3d} -> {value}")
        time.sleep(0.3)  # 300 ms

    demo.close()
