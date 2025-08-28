# QuestDB Highly Available Reads

This is an example repository companion to the blog post XXX, showing how you can get HA reads when using multiple QuestDB
instances.

Replication is out of the box when using QuestDB Enterprise. For QuestDB Open Source, replication can be achieved by
double sending all the DDL and DML commands to several instances, but in that case consistency is not guaranteed.

For the purpose of this demo, and to simplify the setup, you will be just starting three QuestDB instances on a single
host using Docker.

## Start several QuestDB containers using Docker

```sh
docker run --name primary -d --rm -e QDB_CAIRO_WAL_TEMP_PENDING_RENAME_TABLE_PREFIX=temp_primary  -p 9000:9000  -p 8812:8812 questdb/questdb

docker run --name replica1 -d --rm -e QDB_CAIRO_WAL_TEMP_PENDING_RENAME_TABLE_PREFIX=temp_replica1  -p 9001:9000  -p 8813:8812 questdb/questdb

docker run --name replica2 -d --rm -e QDB_CAIRO_WAL_TEMP_PENDING_RENAME_TABLE_PREFIX=temp_replica2  -p 9002:9000  -p 8814:8812 questdb/questdb
```

So I have three instances running, each listening on different HTTP port (9000, 9001, and 9002) and postgresql port (8812, 8813, 8814).
I passed and environment variable to set a different temporary path on each instance; this way, I can easily identify which instance
I am connecting to when examining the parameters configuration with

```sql
select value from (show parameters) where property_path = 'cairo.wal.temp.pending.rename.table.prefix';
```

The property values for my three containers will be `temp_primary`, `temp_replica1`, and `temp_replica2`.

Now that the three containers are running, if you start any of the demos provided they should all show the output
```
temp_primary
temp_primary
temp_primary
```

You can then stop the primary on a separate terminal with:

```
docker stop primary
```

And you should see, after the app notices the connection was failing, that the value has changed to:

```
temp_replica1
temp_replica1
temp_replica1
```

You can keep stopping and restarting containers to see how the demo behaves. When all containers are down, the demo
will just keep retrying until a connection succeeds.

All the demos will send the query 250 times at 300ms intervals and then will exit.


## Python

The demo uses psycopg3. It is easy to adapt to psycopg2, as the overall structure would be the same and psycopg2 also
supports multiple hosts on the connection string.

```sh
pip install -r requirements.txt
python ha_reads.py
```


## Java

The demo is packed as a maven project. You can either execute from your IDE, or use the `mvn` command line:

```sh
mvn compile exec:java -Dexec.mainClass=HAReads
```

## Nodejs

NodeJs does not support natively multiple hots on the connection string. What we are doing here is storing all the hosts
in an array, and test each one in a sequence until successful. The rest of the process is identical to what we are doing
in the other languages.

```sh
npm install
npm start
```

## Dot Net (C#)

```sh
dotnet restore
dotnet run
```

## Go

```sh
go mod tidy
do run .
```

## Rust

```sh
cargo run
```
