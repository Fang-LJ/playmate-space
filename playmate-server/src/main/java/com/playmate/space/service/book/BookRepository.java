package com.playmate.space.service.book;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BookRepository {
    public record Book(Long bookId, String name, Long ownerUserId, String shareCode, String status, Integer version, LocalDateTime updateTime) {}
    public record Member(Long memberId, Long bookId, Long userId, String nickname, String status) {}
    public record Expense(Long expenseId, Long bookId, String title, String category, BigDecimal amount, Long payerMemberId,
                          String splitMode, LocalDateTime expenseTime, String description, Long receiptFileId,
                          Long createdBy, String status, Integer version) {}
    public record Share(Long expenseId, Long memberId, BigDecimal shareAmount, BigDecimal splitRatio) {}
    public record Claim(Long claimId, Long bookId, Long memberId, Long userId, String status) {}
    private static final String BOOK = "SELECT id AS book_id,name,owner_user_id,share_code,status,version,update_time FROM t_expense_book";
    private static final String MEMBER = "SELECT id AS member_id,book_id,user_id,nickname,status FROM t_book_member";
    private static final String EXPENSE = "SELECT id AS expense_id,book_id,title,category,amount,payer_member_id,split_mode,expense_time,description,receipt_file_id,created_by,status,version FROM t_book_expense";
    private static final String CLAIM = "SELECT id AS claim_id,book_id,member_id,user_id,status FROM t_book_claim";
    private final JdbcTemplate jdbc;
    public BookRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0; i<args.length; i++) statement.setObject(i+1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    private <T> List<T> query(Class<T> type, String sql, Object... args) { return jdbc.query(sql, DataClassRowMapper.newInstance(type), args); }
    private <T> T one(Class<T> type, String sql, Object... args) { return query(type, sql, args).stream().findFirst().orElse(null); }
    public Book book(long id, boolean lock) { return one(Book.class, BOOK+" WHERE id=?"+(lock ? " FOR UPDATE" : ""), id); }
    public Book byCode(String code) { return one(Book.class, BOOK+" WHERE share_code=?", code); }
    public Book byRequest(long user, String request) { return one(Book.class, BOOK+" WHERE owner_user_id=? AND client_request_id=?", user, request); }
    public List<Book> list(long user, String status, int offset) {
        return query(Book.class, BOOK+" WHERE status=? AND id IN (SELECT book_id FROM t_book_member WHERE user_id=? AND status='ACTIVE') ORDER BY update_time DESC,id DESC LIMIT 21 OFFSET ?", status,user,offset);
    }
    public List<Member> members(long book) { return query(Member.class, MEMBER+" WHERE book_id=? ORDER BY id",book); }
    public List<Expense> expenses(long book, String category, int offset) {
        return query(Expense.class, EXPENSE+" WHERE book_id=? AND status='ACTIVE'"+(category == null ? "" : " AND category=?")+" ORDER BY expense_time DESC,id DESC LIMIT 31 OFFSET ?",
                category == null ? new Object[]{book,offset} : new Object[]{book,category,offset});
    }
    public List<Expense> allExpenses(long book) { return query(Expense.class, EXPENSE+" WHERE book_id=? AND status='ACTIVE' ORDER BY id", book); }
    public Expense expense(long book,long id) { return one(Expense.class, EXPENSE+" WHERE book_id=? AND id=?",book,id); }
    public Expense expenseRequest(long book,long user,String request) { return one(Expense.class, EXPENSE+" WHERE book_id=? AND created_by=? AND client_request_id=?",book,user,request); }
    public List<Share> shares(long expense) { return query(Share.class,"SELECT expense_id,member_id,share_amount,split_ratio FROM t_book_expense_share WHERE expense_id=? ORDER BY member_id",expense); }
    public List<Share> allShares(long book) { return query(Share.class,"SELECT s.expense_id,s.member_id,s.share_amount,s.split_ratio FROM t_book_expense_share s JOIN t_book_expense e ON e.id=s.expense_id WHERE e.book_id=? AND e.status='ACTIVE'",book); }
    public List<Claim> claims(long book) { return query(Claim.class,CLAIM+" WHERE book_id=? AND status='PENDING' ORDER BY id",book); }
    public Claim claim(long book,long id) { return one(Claim.class,CLAIM+" WHERE book_id=? AND id=?",book,id); }
    public Claim userClaim(long book,long user) { return one(Claim.class,CLAIM+" WHERE book_id=? AND user_id=?",book,user); }
}
