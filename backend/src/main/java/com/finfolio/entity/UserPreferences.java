package com.finfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_preferences_user"))
    private User user;

    @Column(name = "language", nullable = false, length = 10)
    private String language = "es";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)", length = 3)
    private String currency = "EUR";

    @Column(name = "theme", nullable = false, length = 20)
    private String theme = "system";

    @Column(name = "show_net_worth", nullable = false)
    private boolean showNetWorth = true;

    @Column(name = "show_comparisons", nullable = false)
    private boolean showComparisons = true;

    @Column(name = "analytics_consent", nullable = false)
    private boolean analyticsConsent = false;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt = Instant.now();

    public UserPreferences() {
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isShowNetWorth() {
        return showNetWorth;
    }

    public void setShowNetWorth(boolean showNetWorth) {
        this.showNetWorth = showNetWorth;
    }

    public boolean isShowComparisons() {
        return showComparisons;
    }

    public void setShowComparisons(boolean showComparisons) {
        this.showComparisons = showComparisons;
    }

    public boolean isAnalyticsConsent() {
        return analyticsConsent;
    }

    public void setAnalyticsConsent(boolean analyticsConsent) {
        this.analyticsConsent = analyticsConsent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}