package com.sheahorn.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "apikeys")
public class ApiKey extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false, length = 36)
    public String userId;

    @Column(nullable = false)
    public String name;

    @Column(name = "key_hash", nullable = false, length = 128)
    public String keyHash;

    public static ApiKey create(String userId, String name, String keyHash) {
        ApiKey ak = new ApiKey();
        ak.id = UUID.randomUUID().toString();
        ak.userId = userId;
        ak.name = name;
        ak.keyHash = keyHash;
        return ak;
    }

    public static List<ApiKey> findByUserId(String userId) {
        return list("userId", userId);
    }

    public static ApiKey findByKeyHash(String keyHash) {
        return find("keyHash", keyHash).firstResult();
    }
}
