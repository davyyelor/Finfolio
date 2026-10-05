package com.finfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.finfolio.entity.Account;
import com.finfolio.entity.Asset;
import com.finfolio.entity.Document;
import com.finfolio.entity.Holding;
import com.finfolio.entity.Institution;
import com.finfolio.entity.MarketPrice;
import com.finfolio.entity.PortfolioSnapshot;
import com.finfolio.entity.Transaction;
import com.finfolio.entity.TransactionCategory;
import com.finfolio.entity.User;
import com.finfolio.entity.UserPreferences;
import com.finfolio.entity.UserProfile;
import com.finfolio.repository.AccountRepository;
import com.finfolio.repository.AssetRepository;
import com.finfolio.repository.DocumentRepository;
import com.finfolio.repository.HoldingRepository;
import com.finfolio.repository.InstitutionRepository;
import com.finfolio.repository.MarketPriceRepository;
import com.finfolio.repository.PortfolioSnapshotRepository;
import com.finfolio.repository.TransactionCategoryRepository;
import com.finfolio.repository.TransactionRepository;
import com.finfolio.repository.UserPreferencesRepository;
import com.finfolio.repository.UserProfileRepository;
import com.finfolio.repository.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PersistenceTest {

    private static final List<Class<?>> ENTITIES = List.of(
        User.class, UserProfile.class, UserPreferences.class, Institution.class,
        Account.class, Asset.class, Holding.class, TransactionCategory.class,
        Transaction.class, Document.class, MarketPrice.class, PortfolioSnapshot.class
    );

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void validatesSchemaAndDiscoversAllRepositories() {
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
            .isEqualTo("validate");
        assertThat(entityManager.getMetamodel().getEntities())
            .extracting(entity -> entity.getJavaType().getName())
            .containsExactlyInAnyOrderElementsOf(ENTITIES.stream().map(Class::getName).toList());
        assertThat(context.getBeansOfType(JpaRepository.class)).hasSize(12);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Long.class
        )).isEqualTo(1L);
    }

    @Test
    void columnMappingsMatchPostgresTypesLengthsPrecisionAndNullability() {
        for (Class<?> entityType : ENTITIES) {
            String tableName = entityType.getAnnotation(Table.class).name();
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length,
                       numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, tableName);
            Set<String> mappedColumns = new HashSet<>();
            for (Field field : entityType.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                boolean identifier = field.isAnnotationPresent(Id.class);
                if (column == null && joinColumn == null && !identifier) {
                    continue;
                }
                String columnName = column != null ? column.name()
                    : joinColumn != null ? joinColumn.name() : field.getName();
                mappedColumns.add(columnName);
                Map<String, Object> actual = columns.stream()
                    .filter(candidate -> candidate.get("column_name").equals(columnName))
                    .findFirst().orElseThrow();
                String location = tableName + "." + columnName;
                boolean nullable = !identifier && (column != null ? column.nullable() : joinColumn.nullable());
                assertThat(actual.get("is_nullable")).as(location).isEqualTo(nullable ? "YES" : "NO");
                Class<?> javaType = joinColumn != null ? UUID.class : field.getType();
                String sqlType;
                if (javaType == UUID.class) {
                    sqlType = "uuid";
                } else if (javaType == BigDecimal.class) {
                    sqlType = "numeric";
                    assertThat(((Number) actual.get("numeric_precision")).intValue())
                        .as(location).isEqualTo(column.precision());
                    assertThat(((Number) actual.get("numeric_scale")).intValue())
                        .as(location).isEqualTo(column.scale());
                } else if (javaType == Instant.class) {
                    sqlType = "timestamp with time zone";
                } else if (javaType == LocalDate.class) {
                    sqlType = "date";
                } else if (javaType == boolean.class) {
                    sqlType = "boolean";
                } else {
                    assertThat(javaType).as(location).isEqualTo(String.class);
                    sqlType = column.columnDefinition().equals("text") ? "text"
                        : column.columnDefinition().startsWith("char(") ? "character" : "character varying";
                    if (!sqlType.equals("text")) {
                        assertThat(((Number) actual.get("character_maximum_length")).intValue())
                            .as(location).isEqualTo(column.length());
                    }
                }
                assertThat(actual.get("data_type")).as(location).isEqualTo(sqlType);
            }
            assertThat(mappedColumns).as(tableName)
                .containsExactlyInAnyOrderElementsOf(columns.stream()
                    .map(column -> (String) column.get("column_name")).toList());
        }
    }

    @Test
    void namedIndexesAndConstraintsMatchTheExistingSchema() {
        for (Class<?> entityType : ENTITIES) {
            Table table = entityType.getAnnotation(Table.class);
            List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = ?
                """, String.class, table.name());
            List<String> constraints = jdbcTemplate.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = current_schema() AND table_name = ?
                """, String.class, table.name());
            for (var index : table.indexes()) {
                assertThat(indexes).as(table.name()).contains(index.name());
            }
            for (var uniqueConstraint : table.uniqueConstraints()) {
                assertThat(constraints).as(table.name()).contains(uniqueConstraint.name());
            }
            for (var check : table.check()) {
                assertThat(constraints).as(table.name()).contains(check.name());
            }
            for (Field field : entityType.getDeclaredFields()) {
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn == null) {
                    continue;
                }
                if (joinColumn.foreignKey().value() == ConstraintMode.NO_CONSTRAINT) {
                    assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM information_schema.key_column_usage usage
                        JOIN information_schema.table_constraints constraint_info
                          ON constraint_info.constraint_schema = usage.constraint_schema
                         AND constraint_info.constraint_name = usage.constraint_name
                        WHERE usage.table_schema = current_schema() AND usage.table_name = ?
                          AND usage.column_name = ? AND constraint_info.constraint_type = 'FOREIGN KEY'
                        """, Long.class, table.name(), joinColumn.name())).isZero();
                } else {
                    assertThat(constraints).as(table.name()).contains(joinColumn.foreignKey().name());
                }
            }
        }
    }

    @Test
    void repositoriesRoundTripAllEntitiesAndTheirRelationships() {
        Instant timestamp = Instant.parse("2026-10-05T10:15:30.123456Z");
        User user = new User();
        user.setEmail("persistence-" + UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("test-only-not-an-authentication-implementation");
        context.getBean(UserRepository.class).save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setDateOfBirth(LocalDate.of(1990, 5, 12));
        profile.setCountryCode("ES");
        profile.setMonthlyIncome(new BigDecimal("1234.56"));
        profile = context.getBean(UserProfileRepository.class).save(profile);

        UserPreferences preferences = new UserPreferences();
        preferences.setUser(user);
        preferences = context.getBean(UserPreferencesRepository.class).save(preferences);

        Institution institution = new Institution();
        institution.setName("Persistence test institution");
        institution.setType("TEST");
        context.getBean(InstitutionRepository.class).save(institution);

        Account account = new Account();
        account.setUser(user);
        account.setInstitution(institution);
        account.setName("Persistence test account");
        account.setAccountType("TEST");
        account.setCurrentBalance(new BigDecimal("1234.12345678"));
        context.getBean(AccountRepository.class).save(account);

        Asset asset = new Asset();
        asset.setName("Persistence test asset");
        asset.setAssetType("TEST");
        asset.setCurrency("EUR");
        context.getBean(AssetRepository.class).save(asset);

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setAccount(account);
        holding.setAsset(asset);
        holding.setQuantity(new BigDecimal("1.123456789012"));
        holding.setAverageCost(new BigDecimal("12.123456789012"));
        context.getBean(HoldingRepository.class).save(holding);

        TransactionCategory category = new TransactionCategory();
        category.setUser(user);
        category.setName("Persistence test category");
        category.setType("TEST");
        context.getBean(TransactionCategoryRepository.class).save(category);

        Document document = new Document();
        document.setUser(user);
        document.setAccount(account);
        document.setFilename("persistence-test.csv");
        document.setStoragePath("test/persistence-test.csv");
        document.setDocumentType("TEST");
        context.getBean(DocumentRepository.class).save(document);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setAsset(asset);
        transaction.setCategory(category);
        transaction.setDocument(document);
        transaction.setType("TEST");
        transaction.setAmount(new BigDecimal("123.12345678"));
        transaction.setCurrency("EUR");
        transaction.setQuantity(new BigDecimal("1.123456789012"));
        transaction.setPrice(new BigDecimal("12.123456789012"));
        transaction.setTransactionDate(timestamp);
        transaction.setDescription("Long text ".repeat(100));
        context.getBean(TransactionRepository.class).save(transaction);

        MarketPrice marketPrice = new MarketPrice();
        marketPrice.setAsset(asset);
        marketPrice.setTimestamp(timestamp);
        marketPrice.setPrice(new BigDecimal("12.123456789012"));
        marketPrice.setCurrency("EUR");
        context.getBean(MarketPriceRepository.class).save(marketPrice);

        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setUser(user);
        snapshot.setTimestamp(timestamp);
        snapshot.setTotalValue(new BigDecimal("1234.12345678"));
        context.getBean(PortfolioSnapshotRepository.class).save(snapshot);

        entityManager.flush();
        assertThat(profile.getUserId()).isEqualTo(user.getId());
        assertThat(preferences.getUserId()).isEqualTo(user.getId());
        entityManager.clear();

        User storedUser = context.getBean(UserRepository.class).findById(user.getId()).orElseThrow();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
            .isLoaded(storedUser, "accounts")).isFalse();
        assertThat(storedUser.getProfile().getDateOfBirth()).isEqualTo(profile.getDateOfBirth());
        assertThat(storedUser.getProfile().getMonthlyIncome()).isEqualByComparingTo("1234.56");
        assertThat(storedUser.getProfile().getCountryCode()).isEqualTo("ES");
        assertThat(storedUser.getPreferences().getLanguage()).isEqualTo("es");
        assertThat(storedUser.getPreferences().getCurrency()).isEqualTo("EUR");
        assertThat(storedUser.getAccounts()).extracting(Account::getId).containsExactly(account.getId());
        assertThat(storedUser.getHoldings()).extracting(Holding::getId).containsExactly(holding.getId());
        assertThat(storedUser.getTransactions()).extracting(Transaction::getId).containsExactly(transaction.getId());
        assertThat(storedUser.getDocuments()).extracting(Document::getId).containsExactly(document.getId());
        assertThat(storedUser.getPortfolioSnapshots()).extracting(PortfolioSnapshot::getId)
            .containsExactly(snapshot.getId());
        assertThat(storedUser.getTransactionCategories()).extracting(TransactionCategory::getId)
            .containsExactly(category.getId());

        Account storedAccount = context.getBean(AccountRepository.class).findById(account.getId()).orElseThrow();
        assertThat(storedAccount.getCurrentBalance()).isEqualByComparingTo("1234.12345678");
        assertThat(storedAccount.getCurrency()).isEqualTo("EUR");
        assertThat(storedAccount.getInstitution().getAccounts()).extracting(Account::getId)
            .containsExactly(account.getId());
        assertThat(storedAccount.getHoldings()).extracting(Holding::getId).containsExactly(holding.getId());
        assertThat(storedAccount.getTransactions()).extracting(Transaction::getId).containsExactly(transaction.getId());
        assertThat(storedAccount.getDocuments()).extracting(Document::getId).containsExactly(document.getId());

        Asset storedAsset = context.getBean(AssetRepository.class).findById(asset.getId()).orElseThrow();
        assertThat(storedAsset.getHoldings()).extracting(Holding::getId).containsExactly(holding.getId());
        assertThat(storedAsset.getTransactions()).extracting(Transaction::getId).containsExactly(transaction.getId());
        assertThat(storedAsset.getMarketPrices()).extracting(MarketPrice::getId).containsExactly(marketPrice.getId());
        Holding storedHolding = context.getBean(HoldingRepository.class).findById(holding.getId()).orElseThrow();
        assertThat(storedHolding.getQuantity()).isEqualByComparingTo("1.123456789012");
        assertThat(storedHolding.getAverageCost()).isEqualByComparingTo("12.123456789012");

        Transaction storedTransaction = context.getBean(TransactionRepository.class)
            .findById(transaction.getId()).orElseThrow();
        assertThat(storedTransaction.getTransactionDate()).isEqualTo(timestamp);
        assertThat(storedTransaction.getAmount()).isEqualByComparingTo("123.12345678");
        assertThat(storedTransaction.getDescription()).isEqualTo(transaction.getDescription());
        assertThat(storedTransaction.getCategory().getTransactions()).extracting(Transaction::getId)
            .containsExactly(transaction.getId());
        assertThat(storedTransaction.getDocument().getTransactions()).extracting(Transaction::getId)
            .containsExactly(transaction.getId());
        assertThat(storedTransaction.getDocument().getStatus()).isEqualTo("UPLOADED");
        assertThat(storedTransaction.getDocument().getProcessedAt()).isNull();

        MarketPrice storedMarketPrice = context.getBean(MarketPriceRepository.class)
            .findById(marketPrice.getId()).orElseThrow();
        assertThat(storedMarketPrice.getTimestamp()).isEqualTo(timestamp);
        assertThat(storedMarketPrice.getPrice()).isEqualByComparingTo("12.123456789012");
        PortfolioSnapshot storedSnapshot = context.getBean(PortfolioSnapshotRepository.class)
            .findById(snapshot.getId()).orElseThrow();
        assertThat(storedSnapshot.getTotalValue()).isEqualByComparingTo("1234.12345678");
        assertThat(storedSnapshot.getCashValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(storedSnapshot.getInvestmentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(storedSnapshot.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void nullableRelationshipsRemainOptional() {
        User user = new User();
        user.setEmail("optional-" + UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("test-only");
        context.getBean(UserRepository.class).save(user);

        Account account = new Account();
        account.setUser(user);
        account.setName("Account without institution");
        account.setAccountType("TEST");
        context.getBean(AccountRepository.class).save(account);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setType("TEST");
        transaction.setAmount(BigDecimal.ONE);
        transaction.setCurrency("EUR");
        transaction.setTransactionDate(Instant.parse("2026-10-05T10:00:00Z"));
        context.getBean(TransactionRepository.class).save(transaction);

        Document document = new Document();
        document.setUser(user);
        document.setFilename("optional.csv");
        document.setStoragePath("test/optional.csv");
        document.setDocumentType("TEST");
        context.getBean(DocumentRepository.class).save(document);

        TransactionCategory category = new TransactionCategory();
        category.setName("Global test category");
        category.setType("TEST");
        context.getBean(TransactionCategoryRepository.class).save(category);
        entityManager.flush();
        entityManager.clear();

        assertThat(context.getBean(AccountRepository.class).findById(account.getId())
            .orElseThrow().getInstitution()).isNull();
        Transaction stored = context.getBean(TransactionRepository.class).findById(transaction.getId()).orElseThrow();
        assertThat(stored.getAsset()).isNull();
        assertThat(stored.getCategory()).isNull();
        assertThat(stored.getDocument()).isNull();
        assertThat(context.getBean(DocumentRepository.class).findById(document.getId())
            .orElseThrow().getAccount()).isNull();
        assertThat(context.getBean(TransactionCategoryRepository.class).findById(category.getId())
            .orElseThrow().getUser()).isNull();
    }
}