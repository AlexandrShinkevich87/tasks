package cdr.repository.impl;

import cdr.domain.CallDataRecord;
import cdr.repository.CDRRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@Slf4j
@Order(1)
public class CDRRepositoryImpl implements CDRRepository {
    private static final String INSERT_CDR_SQL_QUERY = "INSERT INTO CDR(ID, CALLING_NUM, CALLED_NUMBER, START_TIME, END_TIME, CALL_TYPE, CHARGE, CALL_RESULT) values (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String CREATE_TABLE_SQL_QUERY = "CREATE TABLE IF NOT EXISTS CDR (" +
            "ID VARCHAR(256), " +
            "CALLING_NUM VARCHAR(256), " +
            "CALLED_NUMBER VARCHAR(256), " +
            "START_TIME DATE, " +
            "END_TIME DATE, " +
            "CALL_TYPE VARCHAR(256), " +
            "CHARGE REAL, " +
            "CALL_RESULT VARCHAR(256))";
            ;
    private static final String DELETE_TABLE_SQL_QUERY = "DROP TABLE IF EXISTS CDR CASCADE";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(DELETE_TABLE_SQL_QUERY);
        jdbcTemplate.execute(CREATE_TABLE_SQL_QUERY);
        log.info("User table was created");
    }


    @Override
    public int addCDR(List<CallDataRecord> callDataRecords, int batchSize) {
        int result = 0;

        for (int row = 0; row < callDataRecords.size(); row += batchSize) {
            int toIdx = row + batchSize > callDataRecords.size() ? callDataRecords.size() : row + batchSize;

            final List<CallDataRecord> callDataRecordBatchList = callDataRecords.subList(row, toIdx);

            jdbcTemplate.batchUpdate(INSERT_CDR_SQL_QUERY,

                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            CallDataRecord callDataRecord = callDataRecordBatchList.get(i);
                            ps.setString(1, callDataRecord.getId().toString());
                            ps.setString(2, callDataRecord.getCallingNum());
                            ps.setString(3, callDataRecord.getCalledNumber());
                            ps.setDate(4, new java.sql.Date(callDataRecord.getStartTime().getTime()));
                            ps.setDate(5, new java.sql.Date(callDataRecord.getEndTime().getTime()));
                            ps.setString(6, callDataRecord.getCallType());
                            ps.setFloat(7, callDataRecord.getCharge());
                            ps.setString(8, callDataRecord.getCallResult());
                        }
                        @Override
                        public int getBatchSize() {
                            return callDataRecordBatchList.size();
                        }
                    }
            );
        }

        return result;
    }
}
