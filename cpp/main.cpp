#include <pqxx/pqxx>
#include <iostream>
#include <thread>
#include <chrono>

const std::string ConnStr =
    "host=localhost,localhost,localhost "
    "port=8812,8813,8814 "
    "dbname=qdb user=admin password=quest "
    "target_session_attrs=any "
    "connect_timeout=3";

const std::string Query =
    "select value from (show parameters) "
    "where property_path IN ( 'replication.role', 'cairo.wal.temp.pending.rename.table.prefix') limit 1";

pqxx::connection* conn = nullptr;

pqxx::connection* get_conn_with_retry() {
    while (true) {
        try {
            auto* c = new pqxx::connection(ConnStr);
            if (c->is_open()) {
                std::cout << "Connected to " << c->hostname() << ":" << c->port() << std::endl;
                return c;
            }
        } catch (const std::exception& e) {
            std::cerr << "Connection failed: " << e.what() << std::endl;
        }
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

void run_query(int i) {
    if (!conn || !conn->is_open()) {
        if (conn) delete conn;
        conn = get_conn_with_retry();
    }

    try {
        pqxx::work txn(*conn);
        pqxx::result r = txn.exec(Query);
        std::string val = r[0][0].c_str();
        std::cout << "Query " << i << " -> " << val << std::endl;
        txn.commit();
    } catch (const std::exception& e) {
        std::cerr << "Query error: " << e.what() << " — reconnecting..." << std::endl;
        if (conn) {
            delete conn;
            conn = nullptr;
        }
        run_query(i);  // retry this query
    }
}

int main() {
    for (int i = 1; i <= 250; ++i) {
        run_query(i);
        std::this_thread::sleep_for(std::chrono::milliseconds(300));
    }

    if (conn) {
        delete conn;
    }

    return 0;
}
