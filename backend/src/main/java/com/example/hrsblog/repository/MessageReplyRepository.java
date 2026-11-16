package com.example.hrsblog.repository;

import com.example.hrsblog.domain.MessageReply;
import com.example.hrsblog.dto.MessageReplyRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 管理员留言回复仓储。
 *
 * <p>一条留言至多对应一条回复（编辑回复即覆盖），因此按 {@code message_id} 天然唯一。
 */
public interface MessageReplyRepository extends JpaRepository<MessageReply, Long> {

    /**
     * 批量取留言回复：一次查询拿回整页留言的所有回复，避免留言墙逐条查询（N+1）。
     *
     * <p>返回的是只读投影 {@link MessageReplyRow}，既避免把 {@link MessageReply}
     * 实体连同懒加载的 {@code message} 关联一起拖出来，也避免触发额外查询。
     */
    @Query("select new com.example.hrsblog.dto.MessageReplyRow(r.id, r.message.id, r.content, r.createdAt) "
            + "from MessageReply r where r.message.id in :messageIds")
    List<MessageReplyRow> findByMessageIdIn(@Param("messageIds") Collection<Long> messageIds);

    /** 取单条留言的回复（不存在表示还没有回复）。 */
    Optional<MessageReply> findByMessageId(@Param("messageId") Long messageId);

    /**
     * 删除指定留言的所有回复。
     *
     * <p>派生删除会先查后删；调用方需要处于事务中，并在删除留言本体之前执行，
     * 以满足 {@code message_reply.message_id} 外键的 {@code ON DELETE RESTRICT} 约束。
     */
    long deleteByMessageId(Long messageId);
}
