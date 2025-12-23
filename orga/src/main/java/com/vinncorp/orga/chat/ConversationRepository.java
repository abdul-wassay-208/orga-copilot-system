package com.vinncorp.orga.chat;

import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);
    
    Optional<Conversation> findByIdAndUser(Long id, User user);
    
    @Query("SELECT c FROM Conversation c WHERE c.user = :user ORDER BY c.updatedAt DESC")
    List<Conversation> findUserConversations(@Param("user") User user);
    
    // Tenant-isolated queries
    @Query("SELECT c FROM Conversation c WHERE c.user.tenant = :tenant ORDER BY c.updatedAt DESC")
    List<Conversation> findByTenant(@Param("tenant") Tenant tenant);
    
    @Query("SELECT c FROM Conversation c WHERE c.id = :id AND c.user.tenant = :tenant")
    Optional<Conversation> findByIdAndTenant(@Param("id") Long id, @Param("tenant") Tenant tenant);
}

