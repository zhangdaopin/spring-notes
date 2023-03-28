package example.spring.data.nosql.hbase;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * HBase CRUD 工具类
 *
 * @author <a href="mailto:forbreak@163.com">Zhang Peng</a>
 * @date 2023-03-27
 */
@Slf4j
public class HBaseHelper {

    private final Connection connection;
    private final LoadingCache<String, HBaseTablePool> tablePoolCache =
        Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .refreshAfterWrite(Duration.ofMinutes(1))
                .build(new CacheLoader<String, HBaseTablePool>() {
                    @Override
                    public HBaseTablePool load(String tableName) {
                        HBaseTablePoolFactory factory =
                            new HBaseTablePoolFactory(connection, TableName.valueOf(tableName));
                        GenericObjectPoolConfig<Table> config = new GenericObjectPoolConfig<>();
                        config.setMaxTotal(100);
                        config.setMinIdle(10);
                        config.setBlockWhenExhausted(true);
                        config.setTimeBetweenEvictionRuns(Duration.ofMinutes(10));
                        config.setSoftMinEvictableIdleTime(Duration.ofMinutes(5));
                        config.setEvictionPolicy(new DefaultEvictionPolicy<>());
                        HBaseTablePool pool = new HBaseTablePool(factory, config);
                        log.info("load HBaseTablePool({}) success.", tableName);
                        return pool;
                    }
                });

    protected HBaseHelper(Connection connection) {
        this.connection = connection;
    }

    public static synchronized HBaseHelper newInstance(Connection connection) {
        return new HBaseHelper(connection);
    }

    public Table getTable(String tableName) throws Exception {
        return getTable(TableName.valueOf(tableName));
    }

    public synchronized Table getTable(TableName tableName) throws Exception {
        HBaseTablePool hbaseTablePool = tablePoolCache.getIfPresent(tableName);
        if (hbaseTablePool == null) {
            hbaseTablePool = tablePoolCache.get(Bytes.toString(tableName.getName()));
        }
        return hbaseTablePool.borrowObject();
    }

    public void fillTable(String tableName, int startRow, int stopRow, int colNum, String... families)
        throws Exception {
        fillTable(TableName.valueOf(tableName), startRow, stopRow, colNum, families);
    }

    public void fillTable(TableName tableName, int startRow, int stopRow, int colNum, String... families)
        throws Exception {
        fillTable(tableName, startRow, stopRow, colNum, -1, false, families);
    }

    public void fillTable(String tableName, int startRow, int stopRow, int colNum, boolean setTimestamp,
        String... families) throws Exception {
        fillTable(TableName.valueOf(tableName), startRow, stopRow, colNum, -1, setTimestamp, families);
    }

    public void fillTable(TableName tableName, int startRow, int stopRow, int colNum, boolean setTimestamp,
        String... families) throws Exception {
        fillTable(tableName, startRow, stopRow, colNum, -1, setTimestamp, families);
    }

    public void fillTable(String tableName, int startRow, int stopRow, int colNum, int pad, boolean setTimestamp,
        String... families) throws Exception {
        fillTable(TableName.valueOf(tableName), startRow, stopRow, colNum, pad, setTimestamp, false, families);
    }

    public void fillTable(TableName tableName, int startRow, int stopRow, int colNum, int pad, boolean setTimestamp,
        String... families) throws Exception {
        fillTable(tableName, startRow, stopRow, colNum, pad, setTimestamp, false, families);
    }

    public void fillTable(String tableName, int startRow, int stopRow, int colNum, int pad, boolean setTimestamp,
        boolean random, String... families) throws Exception {
        fillTable(TableName.valueOf(tableName), startRow, stopRow, colNum, pad, setTimestamp, random, families);
    }

    public void fillTable(TableName tableName, int startRow, int stopRow, int colNum, int pad, boolean setTimestamp,
        boolean random, String... families) throws Exception {
        Table table = getTable(tableName);
        Random rnd = new Random();
        for (int row = startRow; row <= stopRow; row++) {
            for (int col = 1; col <= colNum; col++) {
                Put put = new Put(Bytes.toBytes("row-" + padNum(row, pad)));
                for (String family : families) {
                    String colName = "col-" + padNum(col, pad);
                    String value = "value-" + (random ? Integer.toString(rnd.nextInt(colNum))
                        : padNum(row, pad) + "." + padNum(col, pad));
                    if (setTimestamp) {
                        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(colName), col, Bytes.toBytes(value));
                    } else {
                        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(colName), Bytes.toBytes(value));
                    }
                }
                table.put(put);
            }
        }
        recycle(table);
    }

    public void fillTableRandom(String tableName, int minRow, int maxRow, int padRow, int minCol, int maxCol,
        int padCol, int minVal, int maxVal, boolean setTimestamp, String... families) throws Exception {
        fillTableRandom(TableName.valueOf(tableName), minRow, maxRow, padRow, minCol, maxCol, padCol, minVal, maxVal,
            setTimestamp, families);
    }

    public void fillTableRandom(TableName tableName, int minRow, int maxRow, int padRow, int minCol, int maxCol,
        int padCol, int minVal, int maxVal, boolean setTimestamp, String... families) throws Exception {
        Table table = getTable(tableName);
        Random rnd = new Random();
        int maxRows = minRow + rnd.nextInt(maxRow - minRow);
        for (int row = 0; row < maxRows; row++) {
            int maxCols = minCol + rnd.nextInt(maxCol - minCol);
            for (int col = 0; col < maxCols; col++) {
                int rowNum = rnd.nextInt(maxRow - minRow + 1);
                Put put = new Put(Bytes.toBytes("row-" + padNum(rowNum, padRow)));
                for (String family : families) {
                    int colNum = rnd.nextInt(maxCol - minCol + 1);
                    String colName = "col-" + padNum(colNum, padCol);
                    int valNum = rnd.nextInt(maxVal - minVal + 1);
                    String value = "value-" + padNum(valNum, padCol);
                    if (setTimestamp) {
                        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(colName), col, Bytes.toBytes(value));
                    } else {
                        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(colName), Bytes.toBytes(value));
                    }
                }
                table.put(put);
            }
        }
        recycle(table);
    }

    public String padNum(int num, int pad) {
        String res = Integer.toString(num);
        if (pad > 0) {
            while (res.length() < pad) {
                res = "0" + res;
            }
        }
        return res;
    }

    public void put(String tableName, String row, String family, String column, String value) throws Exception {
        put(TableName.valueOf(tableName), row, family, column, value);
    }

    public void put(TableName tableName, String row, String family, String column, String value) throws Exception {
        Table table = getTable(tableName);
        Put put = new Put(Bytes.toBytes(row));
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(column), Bytes.toBytes(value));
        table.put(put);
        recycle(table);
    }

    public void put(String tableName, String row, String family, String column, long ts, String value)
        throws Exception {
        put(TableName.valueOf(tableName), row, family, column, ts, value);
    }

    public void put(TableName tableName, String row, String family, String column, long ts, String value)
        throws Exception {
        Table table = getTable(tableName);
        Put put = new Put(Bytes.toBytes(row));
        put.addColumn(Bytes.toBytes(family), Bytes.toBytes(column), ts, Bytes.toBytes(value));
        table.put(put);
        recycle(table);
    }

    public void put(String tableName, String[] rows, String[] families, String[] columns, long[] ts, String[] values)
        throws Exception {
        put(TableName.valueOf(tableName), rows, families, columns, ts, values);
    }

    public void put(TableName tableName, String[] rows, String[] families, String[] columns, long[] ts, String[] values)
        throws Exception {
        Table table = getTable(tableName);
        for (String row : rows) {
            Put put = new Put(Bytes.toBytes(row));
            for (String family : families) {
                int v = 0;
                for (String column : columns) {
                    String value = values[v < values.length ? v : values.length - 1];
                    long t = ts[v < ts.length ? v : ts.length - 1];
                    // System.out.println("Adding: " + row + " " + family + " " + column +
                    //     " " + t + " " + value);
                    put.addColumn(Bytes.toBytes(family), Bytes.toBytes(column), t, Bytes.toBytes(value));
                    v++;
                }
            }
            table.put(put);
        }
        recycle(table);
    }

    public void put(String tableName, String row, String family, Object obj) throws Exception {
        put(TableName.valueOf(tableName), row, family, obj);
    }

    public void put(TableName tableName, String row, String family, Object obj) throws Exception {
        Put put = new Put(Bytes.toBytes(row));
        Map<String, Object> objectMap = BeanUtil.beanToMap(obj);
        objectMap.forEach((key, value) -> {
            if (value != null) {
                put.addColumn(Bytes.toBytes(family), Bytes.toBytes(key), Bytes.toBytes(String.valueOf(value)));
            }
        });
        Table table = getTable(tableName);
        table.put(put);
    }

    public void deleteRow(String tableName, String row) throws Exception {
        deleteRow(TableName.valueOf(tableName), row);
    }

    public void deleteRow(TableName tableName, String row) throws Exception {
        Delete delete = new Delete(Bytes.toBytes(row));
        Table table = getTable(tableName);
        table.delete(delete);
        recycle(table);
    }

    public void dump(String tableName, String[] rows, String[] families, String[] columns) throws Exception {
        dump(TableName.valueOf(tableName), rows, families, columns);
    }

    public void dump(TableName tableName, String[] rows, String[] families, String[] columns) throws Exception {
        Table table = getTable(tableName);
        List<Get> gets = new ArrayList<>();
        for (String row : rows) {
            Get get = new Get(Bytes.toBytes(row));
            if (families != null) {
                for (String family : families) {
                    for (String column : columns) {
                        get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
                    }
                }
            }
            gets.add(get);
        }
        Result[] results = table.get(gets);
        for (Result result : results) {
            for (Cell cell : result.rawCells()) {
                System.out.println(
                    "Cell: " + cell + ", Value: " + Bytes.toString(cell.getValueArray(), cell.getValueOffset(),
                        cell.getValueLength()));
            }
        }
        recycle(table);
    }

    public void dump(String tableName) throws Exception {
        dump(TableName.valueOf(tableName));
    }

    public void dump(TableName tableName) throws Exception {
        Table table = getTable(tableName);
        ResultScanner scanner = table.getScanner(new Scan());
        for (Result result : scanner) {
            dumpResult(result);
        }
        recycle(table);
    }

    public String get(String tableName, String row, String family, String column) throws Exception {
        return get(TableName.valueOf(tableName), row, family, column);
    }

    public String get(TableName tableName, String row, String family, String column) throws Exception {
        Get get = new Get(Bytes.toBytes(row));
        Table table = getTable(tableName);
        Result result = table.get(get);
        return Bytes.toString(result.getValue(Bytes.toBytes(family), Bytes.toBytes(column)));
    }

    public List<String> get(String tableName, String[] rows, String[] families, String[] columns) throws Exception {
        return get(TableName.valueOf(tableName), rows, families, columns);
    }

    public List<String> get(TableName tableName, String[] rows, String[] families, String[] columns) throws Exception {
        Table table = getTable(tableName);
        List<Get> gets = new ArrayList<>();
        for (String row : rows) {
            Get get = new Get(Bytes.toBytes(row));
            if (families != null) {
                for (String family : families) {
                    for (String column : columns) {
                        get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
                    }
                }
            }
            gets.add(get);
        }

        List<String> list = new ArrayList<>();
        Result[] results = table.get(gets);
        for (Result result : results) {
            for (Cell cell : result.rawCells()) {
                list.add(Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
            }
        }
        recycle(table);
        return list;
    }

    public List<String> scan(String tableName, String startRow, String stopRow, String family, String column)
        throws Exception {
        return scan(TableName.valueOf(tableName), startRow, stopRow, family, column);
    }

    public List<String> scan(TableName tableName, String startRow, String stopRow, String family, String column)
        throws Exception {

        Scan scan = new Scan();
        scan.withStartRow(Bytes.toBytes(startRow));
        scan.withStopRow(Bytes.toBytes(stopRow));

        Table table = getTable(tableName);
        ResultScanner rs = table.getScanner(scan);
        Result result = rs.next();
        for (Cell cell : result.listCells()) {
            cell.getQualifierArray();
        }

        List<String> list = new ArrayList<>();
        while (result != null) {
            String value = Bytes.toString(result.getValue(Bytes.toBytes(family), Bytes.toBytes(column)));
            list.add(value);
            result = rs.next();
        }
        rs.close();
        recycle(table);
        return list;
    }

    public void dumpResult(Result result) {
        for (Cell cell : result.rawCells()) {
            String msg = StrUtil.format("Cell: {}, Value: {}", cell,
                Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
            System.out.println(msg);
        }
    }

    private void recycle(Table table) {
        if (table != null) {
            HBaseTablePool tablePool = tablePoolCache.getIfPresent(table.getName());
            if (tablePool != null) {
                tablePool.returnObject(table);
            }
        }
    }

}
