using System;
using System.Threading;
using Npgsql;
using Npgsql.Internal;

class HAReads
{
    static readonly string[] Hosts = { "localhost", "localhost", "localhost" };
    static readonly int[] Ports = { 8812, 8813, 8814 };

    const string Username = "admin";
    const string Password = "quest";
    const string Database = "qdb";

    const string Query =
        "select value from (show parameters) " +
        "where property_path = 'cairo.wal.temp.pending.rename.table.prefix'";

    static NpgsqlConnection? conn;

    static NpgsqlConnection GetConnWithRetry()
    {
        while (true)
        {
            for (int i = 0; i < Hosts.Length; i++)
            {
                try
                {
                    var builder = new NpgsqlConnectionStringBuilder
                    {
                        Host = Hosts[i],
                        Port = Ports[i],
                        Username = Username,
                        Password = Password,
                        Database = Database,
                        Timeout = 3,
                        ServerCompatibilityMode = ServerCompatibilityMode.NoTypeLoading
                    };

                    var c = new NpgsqlConnection(builder.ConnectionString);
                    c.Open();
                    Console.WriteLine($"Connected to {builder.Host}:{builder.Port}");
                    return c;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Connection to {Hosts[i]}:{Ports[i]} failed: {ex.Message}");
                }
            }

            Thread.Sleep(1000);
        }
    }

    static void RunQuery()
    {
        try
        {
            if (conn == null)
                conn = GetConnWithRetry();

            using var cmd = new NpgsqlCommand(Query, conn);
            using var reader = cmd.ExecuteReader();
            if (reader.Read())
            {
                Console.WriteLine($"Query -> {reader.GetString(0)}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Query failed: {ex.Message}");
            conn?.Close();
            conn = null;
        }
    }

    static void Main()
    {
        for (int i = 0; i < 250; i++)
        {
            RunQuery();
            Thread.Sleep(300);
        }
    }
}
