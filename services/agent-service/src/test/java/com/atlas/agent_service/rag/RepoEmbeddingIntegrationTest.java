package com.atlas.agent_service.rag;

import com.atlas.agent_service.entity.RepoEmbedding;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ATLAS_TEST_DB_URL", matches = ".+")
class RepoEmbeddingIntegrationTest {
    @Test
    void hibernateReadsAndSearchesVectorsUsingTheRealPostgresColumn() {
        var configuration = new Configuration().addAnnotatedClass(RepoEmbedding.class);
        configuration.setProperty("hibernate.connection.url", System.getenv("ATLAS_TEST_DB_URL"));
        configuration.setProperty("hibernate.connection.username", System.getenv("ATLAS_TEST_DB_USERNAME"));
        configuration.setProperty("hibernate.connection.password", System.getenv("ATLAS_TEST_DB_PASSWORD"));
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                var vector = new float[768];
                Arrays.fill(vector, 0.01f);
                var entity = new RepoEmbedding();
                entity.setId(UUID.randomUUID());
                entity.setProjectId(7L);
                entity.setFilePath("__atlas_vector_test_" + entity.getId());
                entity.setChunkIndex(0);
                entity.setContent("Synthetic vector storage test");
                entity.setEmbedding(vector);
                session.persist(entity);
                session.flush();
                session.clear();
                var stored = session.find(RepoEmbedding.class, entity.getId());
                assertArrayEquals(vector, stored.getEmbedding());
                var nearest = session.createNativeQuery("""
                        SELECT * FROM repo_embeddings WHERE id = :id
                        ORDER BY embedding <=> CAST(:vector AS vector) LIMIT 1
                        """, RepoEmbedding.class)
                        .setParameter("id", entity.getId())
                        .setParameter("vector", Arrays.toString(vector)).getSingleResult();
                assertEquals(entity.getId(), nearest.getId());
                assertArrayEquals(vector, nearest.getEmbedding());
            } finally {
                transaction.rollback();
            }
        }
    }
}
