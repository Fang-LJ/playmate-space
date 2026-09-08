package com.playmate.space.service.book;

import com.playmate.space.common.exception.*;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.dto.book.BookRequests;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import com.playmate.space.mapper.FileMapper;
import com.playmate.space.service.book.BookRepository.*;
import com.playmate.space.service.finance.ExpenseCalculator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class BookService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final BookRepository repo;
    private final UserMapper users;
    private final FileMapper files;
    public BookService(BookRepository repo, UserMapper users, FileMapper files) { this.repo=repo; this.users=users; this.files=files; }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Book create(BookRequests.Create request) {
        long user = userId();
        Book existing = repo.byRequest(user,request.clientRequestId());
        if (existing != null) return existing;
        long id;
        try {
            id = repo.insert("INSERT INTO t_expense_book(name,owner_user_id,share_code,client_request_id) VALUES(?,?,?,?)",
                    request.name().trim(),user,UUID.randomUUID().toString().replace("-",""),request.clientRequestId());
        } catch (DuplicateKeyException e) {
            Book concurrent = repo.byRequest(user, request.clientRequestId());
            if (concurrent != null) return concurrent;
            throw e;
        }
        repo.insert("INSERT INTO t_book_member(book_id,user_id,nickname) VALUES(?,?,?)",id,user,memberName(user));
        for (String name : request.names()) repo.insert("INSERT INTO t_book_member(book_id,nickname) VALUES(?,?)",id,name.trim());
        return repo.book(id,false);
    }
    public Map<String,Object> list(String status,int page) {
        if (!Set.of("OPEN","ARCHIVED").contains(status)) throw invalid("账本状态不支持");
        checkPage(page); long user = userId(); var rows=repo.list(user,status,(page-1)*20);
        return map("items",rows.stream().limit(20).map(b -> {
            var summary = dashboardData(b,user);
            return map("bookId",b.bookId(),"name",b.name(),"status",b.status(),"isOwner",b.ownerUserId().equals(user),
                    "totalAmount",summary.get("totalAmount"),"memberCount",summary.get("memberCount"),"myNetAmount",summary.get("myNetAmount"));
        }).toList(),"hasMore",rows.size()>20);
    }
    public Map<String,Object> dashboard(long id) { Book book=access(id,false); return dashboardData(book,userId()); }
    private Map<String,Object> dashboardData(Book book,long user) {
        var members=repo.members(book.bookId()); var expenses=repo.allExpenses(book.bookId()); var shares=repo.allShares(book.bookId());
        Map<Long,BigDecimal> paid=new TreeMap<>(), owed=new TreeMap<>(), net=new TreeMap<>();
        members.forEach(m -> { paid.put(m.memberId(),ZERO); owed.put(m.memberId(),ZERO); });
        expenses.forEach(e -> paid.merge(e.payerMemberId(),e.amount(),BigDecimal::add));
        shares.forEach(s -> owed.merge(s.memberId(),s.shareAmount(),BigDecimal::add));
        members.forEach(m -> net.put(m.memberId(),paid.get(m.memberId()).subtract(owed.get(m.memberId()))));
        Member mine=members.stream().filter(m -> Objects.equals(m.userId(),user)).findFirst().orElse(null);
        var names=members.stream().collect(Collectors.toMap(Member::memberId,Member::nickname));
        var suggestions=ExpenseCalculator.settle(net).stream().map(t -> map("fromMemberId",t.fromId(),"toMemberId",t.toId(),
                "fromNickname",names.get(t.fromId()),"toNickname",names.get(t.toId()),"amount",t.amount())).toList();
        return map("book",book,"isOwner",book.ownerUserId().equals(user),"myMemberId",mine==null?null:mine.memberId(),
                "myNetAmount",mine==null?ZERO:net.get(mine.memberId()),"totalAmount",expenses.stream().map(Expense::amount).reduce(ZERO,BigDecimal::add),
                "expenseCount",expenses.size(),"memberCount",members.stream().filter(m -> "ACTIVE".equals(m.status())).count(),
                "members",members.stream().map(m -> map("memberId",m.memberId(),"userId",m.userId(),"nickname",m.nickname(),"status",m.status(),
                        "paidAmount",paid.get(m.memberId()),"shareAmount",owed.get(m.memberId()),"netAmount",net.get(m.memberId()))).toList(),
                "suggestions",suggestions,"claims",book.ownerUserId().equals(user)?repo.claims(book.bookId()).stream().map(c -> map(
                        "claimId",c.claimId(),"memberId",c.memberId(),"memberNickname",names.get(c.memberId()),"applicantNickname",userName(c.userId()))).toList():List.of());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Book rename(long id,BookRequests.Rename request) {
        Book b=owner(id); version(b.version(),request.version());
        repo.update("UPDATE t_expense_book SET name=? WHERE id=?",request.name().trim(),id); touch(id); return repo.book(id,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Book state(long id,BookRequests.State request) {
        Book b=access(id,true); requireOwner(b); version(b.version(),request.version());
        if (!b.status().equals(request.status())) { repo.update("UPDATE t_expense_book SET status=? WHERE id=?",request.status(),id); touch(id); }
        return repo.book(id,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Member addMember(long id,BookRequests.Member request) {
        owner(id); var members=repo.members(id); capacity(members);
        long memberId=repo.insert("INSERT INTO t_book_member(book_id,nickname) VALUES(?,?)",id,request.nickname().trim()); touch(id);
        return member(id,memberId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Member renameMember(long id,long memberId,BookRequests.Member request) {
        owner(id); member(id,memberId);
        repo.update("UPDATE t_book_member SET nickname=? WHERE book_id=? AND id=?",request.nickname().trim(),id,memberId); touch(id); return member(id,memberId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Member memberState(long id,long memberId,BookRequests.MemberState request) {
        Book b=owner(id); Member m=member(id,memberId);
        if (Objects.equals(m.userId(),b.ownerUserId())) throw invalid("不能停用创建者");
        repo.update("UPDATE t_book_member SET status=? WHERE book_id=? AND id=?",request.status(),id,memberId); touch(id); return member(id,memberId);
    }
    public Map<String,Object> invite(String code) {
        Book b=repo.byCode(code); if (b==null) throw new NotFoundException("邀请不存在或已失效");
        // Public preview intentionally contains no balances, expenses, account IDs or receipts.
        return map("name",b.name(),"status",b.status(),"creatorNickname",userName(b.ownerUserId()),
                "members","OPEN".equals(b.status())?repo.members(b.bookId()).stream().filter(m -> m.userId()==null && "ACTIVE".equals(m.status()))
                        .map(m -> map("memberId",m.memberId(),"nickname",m.nickname())).toList():List.of());
    }
    public Map<String,Object> joinStatus(String code) {
        long user=userId(); Book b=repo.byCode(code); if(b==null) throw new NotFoundException("邀请不存在或已失效");
        Member existing=repo.members(b.bookId()).stream().filter(m -> Objects.equals(m.userId(),user)).findFirst().orElse(null);
        if(existing!=null) {
            if(!"ACTIVE".equals(existing.status())) throw new ForbiddenException("你已被停用，请联系创建者");
            return map("status","JOINED","bookId",b.bookId());
        }
        Claim claim=repo.userClaim(b.bookId(),user);
        return map("status",claim==null?"NEW":claim.status());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> join(BookRequests.Join request) {
        long user=userId(); Book found=repo.byCode(request.code()); if (found==null) throw new NotFoundException("邀请不存在或已失效");
        Book b=repo.book(found.bookId(),true); var members=repo.members(b.bookId());
        Member existing=members.stream().filter(m -> Objects.equals(m.userId(),user)).findFirst().orElse(null);
        if (existing!=null) {
            if (!"ACTIVE".equals(existing.status())) throw new ForbiddenException("你已被停用，请联系创建者");
            return map("bookId",b.bookId(),"status","JOINED");
        }
        writable(b);
        Claim pending=repo.userClaim(b.bookId(),user);
        if (pending!=null && "PENDING".equals(pending.status())) return map("status","PENDING");
        if (request.memberId()!=null) {
            Member target=member(b.bookId(),request.memberId());
            if (target.userId()!=null || !"ACTIVE".equals(target.status())) throw invalid("该成员已关联账号或已停用");
            repo.update("INSERT INTO t_book_claim(book_id,member_id,user_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE member_id=VALUES(member_id),status='PENDING',create_time=CURRENT_TIMESTAMP",b.bookId(),target.memberId(),user);
            touch(b.bookId()); return map("status","PENDING");
        }
        capacity(members); repo.insert("INSERT INTO t_book_member(book_id,user_id,nickname) VALUES(?,?,?)",b.bookId(),user,memberName(user));
        touch(b.bookId()); return map("bookId",b.bookId(),"status","JOINED");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public void decide(long id,long claimId,BookRequests.Decision request) {
        owner(id); Claim c=repo.claim(id,claimId); if (c==null) throw new NotFoundException("申请不存在");
        if (!"PENDING".equals(c.status())) throw invalid("该申请已处理，请刷新");
        if (request.approve()) {
            Member target=member(id,c.memberId());
            if (target.userId()!=null || !"ACTIVE".equals(target.status())) throw invalid("该成员已关联或已停用");
            if (repo.members(id).stream().anyMatch(m -> Objects.equals(m.userId(),c.userId()))) throw invalid("申请人已经是成员");
            requireUser(c.userId());
            repo.update("UPDATE t_book_member SET user_id=? WHERE book_id=? AND id=? AND user_id IS NULL",c.userId(),id,target.memberId());
            repo.update("UPDATE t_book_claim SET status='REJECTED' WHERE book_id=? AND member_id=? AND status='PENDING' AND id<>?",id,target.memberId(),claimId);
        }
        repo.update("UPDATE t_book_claim SET status=? WHERE book_id=? AND id=?",request.approve()?"APPROVED":"REJECTED",id,claimId); touch(id);
    }
    public Map<String,Object> expenses(long id,String category,int page) {
        access(id,false); checkPage(page); var rows=repo.expenses(id,category,(page-1)*30);
        var members=repo.members(id); var names=members.stream().collect(Collectors.toMap(Member::memberId,Member::nickname));
        return map("items",rows.stream().limit(30).map(e -> map("expenseId",e.expenseId(),"title",e.title(),"amount",e.amount(),
                "category",e.category(),"payerNickname",names.get(e.payerMemberId()),"expenseTime",e.expenseTime())).toList(),"hasMore",rows.size()>30);
    }
    public Map<String,Object> expense(long id,long expenseId) { Book b=access(id,false); return detail(b,requireExpense(id,expenseId)); }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> save(long id,Long expenseId,BookRequests.Expense request) {
        Book b=access(id,true); writable(b); long user=userId(); Expense existing=null;
        if (expenseId==null) {
            if (request.clientRequestId()==null || request.clientRequestId().isBlank()) throw invalid("新增消费需要请求编号");
            existing=repo.expenseRequest(id,user,request.clientRequestId()); if(existing!=null) return detail(b,existing);
        } else {
            existing=requireExpense(id,expenseId); requireEditor(b,existing,user); version(existing.version(),request.version());
            if (!"ACTIVE".equals(existing.status())) throw invalid("已作废消费不能编辑");
        }
        Member payer=activeMember(id,request.payerMemberId());
        if (!b.ownerUserId().equals(user) && !Objects.equals(payer.userId(),user)) throw new ForbiddenException("普通成员只能记录自己付款的消费");
        request.shares().forEach(s -> activeMember(id,s.memberId()));
        var shares=ExpenseCalculator.split(request.amount(),request.splitMode(),request.shares().stream()
                .map(s -> new ExpenseCalculator.Share(s.memberId(),s.shareAmount(),s.splitRatio())).toList());
        validateReceipt(existing==null?null:existing.receiptFileId(),request.receiptFileId(),user);
        if (expenseId==null) expenseId=repo.insert("INSERT INTO t_book_expense(book_id,title,category,amount,payer_member_id,split_mode,expense_time,description,receipt_file_id,created_by,client_request_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id,request.title().trim(),request.category(),request.amount(),payer.memberId(),request.splitMode(),request.expenseTime(),request.description(),request.receiptFileId(),user,request.clientRequestId());
        else {
            int updated=repo.update("UPDATE t_book_expense SET title=?,category=?,amount=?,payer_member_id=?,split_mode=?,expense_time=?,description=?,receipt_file_id=?,version=version+1,update_time=CURRENT_TIMESTAMP WHERE book_id=? AND id=? AND version=? AND status='ACTIVE'",
                    request.title().trim(),request.category(),request.amount(),payer.memberId(),request.splitMode(),request.expenseTime(),request.description(),request.receiptFileId(),id,expenseId,request.version());
            if(updated!=1) throw invalid("消费已修改，请刷新后重试");
            repo.update("DELETE FROM t_book_expense_share WHERE expense_id=?",expenseId);
        }
        for(var share:request.shares()) repo.update("INSERT INTO t_book_expense_share(expense_id,member_id,share_amount,split_ratio) VALUES(?,?,?,?)",
                expenseId,share.memberId(),shares.get(share.memberId()),"PROPORTIONAL".equals(request.splitMode())?share.splitRatio():BigDecimal.ONE);
        touch(id); return detail(repo.book(id,false),requireExpense(id,expenseId));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public void voidExpense(long id,long expenseId,BookRequests.VoidExpense request) {
        Book b=access(id,true); writable(b); Expense e=requireExpense(id,expenseId); requireEditor(b,e,userId());
        if(repo.update("UPDATE t_book_expense SET status='VOID',version=version+1,update_time=CURRENT_TIMESTAMP WHERE book_id=? AND id=? AND version=? AND status='ACTIVE'",id,expenseId,request.expectedVersion())!=1) throw invalid("消费已修改或作废，请刷新后重试");
        touch(id);
    }
    private Map<String,Object> detail(Book b,Expense e) {
        var members=repo.members(b.bookId()); var names=members.stream().collect(Collectors.toMap(Member::memberId,Member::nickname));
        var file=e.receiptFileId()==null?null:files.selectById(e.receiptFileId()); long user=userId();
        return map("expenseId",e.expenseId(),"bookId",e.bookId(),"title",e.title(),"category",e.category(),"amount",e.amount(),
                "payerMemberId",e.payerMemberId(),"payerNickname",names.get(e.payerMemberId()),"createdBy",e.createdBy(),"creatorNickname",userName(e.createdBy()),
                "expenseTime",e.expenseTime(),"splitMode",e.splitMode(),"description",e.description(),"status",e.status(),"version",e.version(),
                "receiptFileId",e.receiptFileId(),"receiptUrl",file==null?null:file.getUrl(),
                "canEdit","OPEN".equals(b.status())&&"ACTIVE".equals(e.status())&&(b.ownerUserId().equals(user)||e.createdBy().equals(user)),
                "shares",repo.shares(e.expenseId()).stream().map(s -> map("memberId",s.memberId(),"nickname",names.get(s.memberId()),"shareAmount",s.shareAmount(),"splitRatio",s.splitRatio())).toList());
    }
    private void validateReceipt(Long previous,Long next,long user) {
        if(next==null || next.equals(previous)) return;
        var file=files.selectById(next);
        if(file==null || !Objects.equals(file.getUploadUserId(),user) || !"EXPENSE_RECEIPT".equals(file.getFileType()) || !"NORMAL".equals(file.getStatus())) throw invalid("付款凭证无效或不属于当前用户");
    }
    private Book access(long id,boolean lock) {
        long user=userId(); Book b=repo.book(id,lock); if(b==null) throw new NotFoundException("账本不存在");
        if(repo.members(id).stream().noneMatch(m -> Objects.equals(m.userId(),user)&&"ACTIVE".equals(m.status()))) throw new ForbiddenException("请先加入账本");
        return b;
    }
    private Book owner(long id) { Book b=access(id,true); requireOwner(b); writable(b); return b; }
    private void requireOwner(Book b) { if(!b.ownerUserId().equals(userId())) throw new ForbiddenException("仅创建者可以操作"); }
    private void writable(Book b) { if(!"OPEN".equals(b.status())) throw new ForbiddenException("账本已归档，请创建者重新打开后修改"); }
    private Member member(long id,long memberId) { return repo.members(id).stream().filter(m -> m.memberId().equals(memberId)).findFirst().orElseThrow(() -> new NotFoundException("成员不属于该账本")); }
    private Member activeMember(long id,long memberId) { Member m=member(id,memberId); if(!"ACTIVE".equals(m.status())) throw invalid("该成员已停用，请先恢复成员"); return m; }
    private Expense requireExpense(long id,long expenseId) { var e=repo.expense(id,expenseId); if(e==null) throw new NotFoundException("消费不存在"); return e; }
    private void requireEditor(Book b,Expense e,long user) { if(!b.ownerUserId().equals(user)&&!e.createdBy().equals(user)) throw new ForbiddenException("仅记录人或创建者可以修改"); }
    private void version(Integer expected,Integer actual) { if(!Objects.equals(expected,actual)) throw invalid("内容已被修改，请刷新后重试"); }
    private void capacity(List<Member> members) { if(members.size()>=50) throw invalid("一个账本最多添加 50 位成员"); }
    private void touch(long id) { repo.update("UPDATE t_expense_book SET version=version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id); }
    private void checkPage(int page) { if(page<1 || page>10000) throw invalid("页码不合法"); }
    private long userId() { Long id=LoginUserContext.getUserId(); if(id==null) throw new UnauthorizedException(); requireUser(id); return id; }
    private UserEntity requireUser(long id) { var user=users.selectById(id); if(user==null) throw new UnauthorizedException(); if(!"NORMAL".equals(user.getStatus())) throw new ForbiddenException("账号已停用"); return user; }
    private String memberName(long id) { String name=userName(id); return name.length()>40?name.substring(0,40):name; }
    private String userName(long id) { var user=users.selectById(id); return user==null || user.getNickname()==null || user.getNickname().isBlank()?"玩伴用户":user.getNickname(); }
    private static BusinessException invalid(String message) { return new BusinessException("PARAM_ERROR",message); }
    private static Map<String,Object> map(Object... entries) { Map<String,Object> result=new LinkedHashMap<>(); for(int i=0;i<entries.length;i+=2) result.put((String)entries[i],entries[i+1]); return result; }
}
