/*
 * Copyright 2019-2024 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.polypheny.db.TestHelper;

public class ConcurrencyTests {

    private static TestHelper testHelper;


    @BeforeAll
    public static void setUpClass() {
        testHelper = TestHelper.getInstance();
    }


    private void setupAlterTable1() {
        List.of(
                "CREATE TABLE a (i INT PRIMARY KEY);",
                "CREATE TABLE b (a_id INT PRIMARY KEY, a_ref INT NULL);",
                "INSERT INTO a (i) VALUES (0), (1), (2), (3);",
                "INSERT INTO b (a_id) VALUES (0), (1), (2), (3), (4);"
        ).forEach( s -> {
            Transaction transaction = testHelper.getTransaction();
            ConcurrencyTestUtils.executeStatement( s, "sql", transaction, testHelper );
            transaction.commit();
        } );
    }


    private void cleanupAlterTables1() {
        List.of(
                "DROP TABLE IF EXISTS b;",
                "DROP TABLE IF EXISTS a;"
        ).forEach( s -> {
            Transaction transaction = testHelper.getTransaction();
            ConcurrencyTestUtils.executeStatement( s, "sql", transaction, testHelper );
            transaction.commit();
        } );
    }


    @Test
    public void alterTable1() {
        Session session1 = new Session( testHelper );
        Session session2 = new Session( testHelper );
        Set<Session> sessions = Set.of( session1, session2 );

        Map<String, Runnable> operations = new HashMap<>();
        operations.put("init", this::setupAlterTable1 );
        operations.put("clean", this::cleanupAlterTables1 );

        operations.put( "s1", session1::startTransaction );
        operations.put( "at1", () -> session1.executeStatement(
                "ALTER TABLE b ADD CONSTRAINT bfk FOREIGN KEY (a_ref) REFERENCES a (i);",
                "sql"
        ) );
        operations.put( "sc1", session1::commitTransaction );

        operations.put( "s2", session1::startTransaction );
        operations.put( "at2", () -> session1.executeStatement(
                "ALTER TABLE b ADD INDEX bid ON a_ref;",
                "sql"
        ) );
        operations.put( "sc2", session1::commitTransaction );

        operations.put( "s3", session2::startTransaction );
        operations.put( "rx1", () -> session2.executeStatement(
                "SELECT * FROM b WHERE a_id = 1 LIMIT 1;",
                "sql"
        ) );
        operations.put( "wx", () -> session2.executeStatement(
                "INSERT INTO b (a_id, a_ref) VALUES (0, NULL);",
                "sql"
        ) );
        operations.put( "rx3", () -> session2.executeStatement(
                "SELECT * FROM b WHERE a_id = 3 LIMIT 3;",
                "sql"
        ) );
        operations.put( "c2", session2::commitTransaction );

        List<String> executions = List.of(
                "s1 at1 sc1 s2 at2 s3 rx1 wx rx3 sc2 c2",
                "s1 at1 sc1 s2 at2 s3 rx1 sc2 wx rx3 c2",
                "s1 at1 sc1 s2 s3 rx1 at2 sc2 wx rx3 c2",
                "s1 at1 sc1 s2 s3 rx1 at2 wx sc2 rx3 c2",
                "s1 at1 sc1 s2 s3 rx1 at2 wx rx3 sc2 c2",
                "s1 at1 sc1 s2 s3 rx1 at2 wx rx3 c2 sc2",
                "s1 at1 sc1 s2 s3 rx1 wx at2 sc2 rx3 c2",
                "s1 at1 sc1 s2 s3 rx1 wx at2 rx3 sc2 c2",
                "s1 at1 sc1 s2 s3 rx1 wx at2 rx3 c2 sc2",
                "s1 at1 sc1 s2 s3 rx1 wx rx3 at2 sc2 c2",
                "s1 at1 sc1 s2 s3 rx1 wx rx3 at2 c2 sc2",
                "s1 at1 sc1 s2 s3 rx1 wx rx3 c2 at2 sc2",
                "s1 at1 sc1 s3 rx1 s2 at2 sc2 wx rx3 c2",
                "s1 at1 sc1 s3 rx1 s2 at2 wx sc2 rx3 c2",
                "s1 at1 sc1 s3 rx1 s2 at2 wx rx3 sc2 c2",
                "s1 at1 sc1 s3 rx1 s2 at2 wx rx3 c2 sc2",
                "s1 at1 sc1 s3 rx1 s2 wx at2 sc2 rx3 c2",
                "s1 at1 sc1 s3 rx1 s2 wx at2 rx3 sc2 c2",
                "s1 at1 sc1 s3 rx1 s2 wx at2 rx3 c2 sc2",
                "s1 at1 sc1 s3 rx1 s2 wx rx3 at2 sc2 c2",
                "s1 at1 sc1 s3 rx1 s2 wx rx3 at2 c2 sc2",
                "s1 at1 sc1 s3 rx1 s2 wx rx3 c2 at2 sc2",
                "s1 at1 sc1 s3 rx1 wx s2 at2 sc2 rx3 c2",
                "s1 at1 sc1 s3 rx1 wx s2 at2 rx3 sc2 c2",
                "s1 at1 sc1 s3 rx1 wx s2 at2 rx3 c2 sc2",
                "s1 at1 sc1 s3 rx1 wx s2 rx3 at2 sc2 c2",
                "s1 at1 sc1 s3 rx1 wx s2 rx3 at2 c2 sc2",
                "s1 at1 sc1 s3 rx1 wx s2 rx3 c2 at2 sc2",
                "s1 at1 sc1 s3 rx1 wx rx3 s2 at2 sc2 c2",
                "s1 at1 sc1 s3 rx1 wx rx3 s2 at2 c2 sc2",
                "s1 at1 sc1 s3 rx1 wx rx3 s2 c2 at2 sc2",
                "s1 at1 sc1 s3 rx1 wx rx3 c2 s2 at2 sc2",
                "s1 at1 s3 rx1 sc1 s2 at2 sc2 wx rx3 c2",
                "s1 at1 s3 rx1 sc1 s2 at2 wx sc2 rx3 c2",
                "s1 at1 s3 rx1 sc1 s2 at2 wx rx3 sc2 c2",
                "s1 at1 s3 rx1 sc1 s2 at2 wx rx3 c2 sc2",
                "s1 at1 s3 rx1 sc1 s2 wx at2 sc2 rx3 c2",
                "s1 at1 s3 rx1 sc1 s2 wx at2 rx3 sc2 c2",
                "s1 at1 s3 rx1 sc1 s2 wx at2 rx3 c2 sc2",
                "s1 at1 s3 rx1 sc1 s2 wx rx3 at2 sc2 c2",
                "s1 at1 s3 rx1 sc1 s2 wx rx3 at2 c2 sc2",
                "s1 at1 s3 rx1 sc1 s2 wx rx3 c2 at2 sc2",
                "s1 at1 s3 rx1 sc1 wx s2 at2 sc2 rx3 c2",
                "s1 at1 s3 rx1 sc1 wx s2 at2 rx3 sc2 c2",
                "s1 at1 s3 rx1 sc1 wx s2 at2 rx3 c2 sc2",
                "s1 at1 s3 rx1 sc1 wx s2 rx3 at2 sc2 c2",
                "s1 at1 s3 rx1 sc1 wx s2 rx3 at2 c2 sc2",
                "s1 at1 s3 rx1 sc1 wx s2 rx3 c2 at2 sc2",
                "s1 at1 s3 rx1 sc1 wx rx3 s2 at2 sc2 c2",
                "s1 at1 s3 rx1 sc1 wx rx3 s2 at2 c2 sc2",
                "s1 at1 s3 rx1 sc1 wx rx3 s2 c2 at2 sc2",
                "s1 at1 s3 rx1 sc1 wx rx3 c2 s2 at2 sc2",
                "s1 at1 s3 rx1 wx sc1 s2 at2 sc2 rx3 c2",
                "s1 at1 s3 rx1 wx sc1 s2 at2 rx3 sc2 c2",
                "s1 at1 s3 rx1 wx sc1 s2 at2 rx3 c2 sc2",
                "s1 at1 s3 rx1 wx sc1 s2 rx3 at2 sc2 c2",
                "s1 at1 s3 rx1 wx sc1 s2 rx3 at2 c2 sc2",
                "s1 at1 s3 rx1 wx sc1 s2 rx3 c2 at2 sc2",
                "s1 at1 s3 rx1 wx sc1 rx3 s2 at2 sc2 c2",
                "s1 at1 s3 rx1 wx sc1 rx3 s2 at2 c2 sc2",
                "s1 at1 s3 rx1 wx sc1 rx3 s2 c2 at2 sc2",
                "s1 at1 s3 rx1 wx sc1 rx3 c2 s2 at2 sc2",
                "s1 s3 rx1 at1 sc1 s2 at2 sc2 wx rx3 c2",
                "s1 s3 rx1 at1 sc1 s2 at2 wx sc2 rx3 c2",
                "s1 s3 rx1 at1 sc1 s2 at2 wx rx3 sc2 c2",
                "s1 s3 rx1 at1 sc1 s2 at2 wx rx3 c2 sc2",
                "s1 s3 rx1 at1 sc1 s2 wx at2 sc2 rx3 c2",
                "s1 s3 rx1 at1 sc1 s2 wx at2 rx3 sc2 c2",
                "s1 s3 rx1 at1 sc1 s2 wx at2 rx3 c2 sc2",
                "s1 s3 rx1 at1 sc1 s2 wx rx3 at2 sc2 c2",
                "s1 s3 rx1 at1 sc1 s2 wx rx3 at2 c2 sc2",
                "s1 s3 rx1 at1 sc1 s2 wx rx3 c2 at2 sc2",
                "s1 s3 rx1 at1 sc1 wx s2 at2 sc2 rx3 c2",
                "s1 s3 rx1 at1 sc1 wx s2 at2 rx3 sc2 c2",
                "s1 s3 rx1 at1 sc1 wx s2 at2 rx3 c2 sc2",
                "s1 s3 rx1 at1 sc1 wx s2 rx3 at2 sc2 c2",
                "s1 s3 rx1 at1 sc1 wx s2 rx3 at2 c2 sc2",
                "s1 s3 rx1 at1 sc1 wx s2 rx3 c2 at2 sc2",
                "s1 s3 rx1 at1 sc1 wx rx3 s2 at2 sc2 c2",
                "s1 s3 rx1 at1 sc1 wx rx3 s2 at2 c2 sc2",
                "s1 s3 rx1 at1 sc1 wx rx3 s2 c2 at2 sc2",
                "s1 s3 rx1 at1 sc1 wx rx3 c2 s2 at2 sc2",
                "s1 s3 rx1 at1 wx sc1 s2 at2 sc2 rx3 c2",
                "s1 s3 rx1 at1 wx sc1 s2 at2 rx3 sc2 c2",
                "s1 s3 rx1 at1 wx sc1 s2 at2 rx3 c2 sc2",
                "s1 s3 rx1 at1 wx sc1 s2 rx3 at2 sc2 c2",
                "s1 s3 rx1 at1 wx sc1 s2 rx3 at2 c2 sc2",
                "s1 s3 rx1 at1 wx sc1 s2 rx3 c2 at2 sc2",
                "s1 s3 rx1 at1 wx sc1 rx3 s2 at2 sc2 c2",
                "s1 s3 rx1 at1 wx sc1 rx3 s2 at2 c2 sc2",
                "s1 s3 rx1 at1 wx sc1 rx3 s2 c2 at2 sc2",
                "s1 s3 rx1 at1 wx sc1 rx3 c2 s2 at2 sc2",
                "s1 s3 rx1 wx at1 rx3 c2 sc1 s2 at2 sc2",
                "s1 s3 rx1 wx rx3 at1 c2 sc1 s2 at2 sc2",
                "s1 s3 rx1 wx rx3 c2 at1 sc1 s2 at2 sc2",
                "s3 rx1 s1 at1 sc1 s2 at2 sc2 wx rx3 c2",
                "s3 rx1 s1 at1 sc1 s2 at2 wx sc2 rx3 c2",
                "s3 rx1 s1 at1 sc1 s2 at2 wx rx3 sc2 c2",
                "s3 rx1 s1 at1 sc1 s2 at2 wx rx3 c2 sc2",
                "s3 rx1 s1 at1 sc1 s2 wx at2 sc2 rx3 c2",
                "s3 rx1 s1 at1 sc1 s2 wx at2 rx3 sc2 c2",
                "s3 rx1 s1 at1 sc1 s2 wx at2 rx3 c2 sc2",
                "s3 rx1 s1 at1 sc1 s2 wx rx3 at2 sc2 c2",
                "s3 rx1 s1 at1 sc1 s2 wx rx3 at2 c2 sc2",
                "s3 rx1 s1 at1 sc1 s2 wx rx3 c2 at2 sc2",
                "s3 rx1 s1 at1 sc1 wx s2 at2 sc2 rx3 c2",
                "s3 rx1 s1 at1 sc1 wx s2 at2 rx3 sc2 c2",
                "s3 rx1 s1 at1 sc1 wx s2 at2 rx3 c2 sc2",
                "s3 rx1 s1 at1 sc1 wx s2 rx3 at2 sc2 c2",
                "s3 rx1 s1 at1 sc1 wx s2 rx3 at2 c2 sc2",
                "s3 rx1 s1 at1 sc1 wx s2 rx3 c2 at2 sc2",
                "s3 rx1 s1 at1 sc1 wx rx3 s2 at2 sc2 c2",
                "s3 rx1 s1 at1 sc1 wx rx3 s2 at2 c2 sc2",
                "s3 rx1 s1 at1 sc1 wx rx3 s2 c2 at2 sc2",
                "s3 rx1 s1 at1 sc1 wx rx3 c2 s2 at2 sc2",
                "s3 rx1 s1 at1 wx sc1 s2 at2 sc2 rx3 c2",
                "s3 rx1 s1 at1 wx sc1 s2 at2 rx3 sc2 c2",
                "s3 rx1 s1 at1 wx sc1 s2 at2 rx3 c2 sc2",
                "s3 rx1 s1 at1 wx sc1 s2 rx3 at2 sc2 c2",
                "s3 rx1 s1 at1 wx sc1 s2 rx3 at2 c2 sc2",
                "s3 rx1 s1 at1 wx sc1 s2 rx3 c2 at2 sc2",
                "s3 rx1 s1 at1 wx sc1 rx3 s2 at2 sc2 c2",
                "s3 rx1 s1 at1 wx sc1 rx3 s2 at2 c2 sc2",
                "s3 rx1 s1 at1 wx sc1 rx3 s2 c2 at2 sc2",
                "s3 rx1 s1 at1 wx sc1 rx3 c2 s2 at2 sc2",
                "s3 rx1 s1 wx at1 rx3 c2 sc1 s2 at2 sc2",
                "s3 rx1 s1 wx rx3 at1 c2 sc1 s2 at2 sc2",
                "s3 rx1 s1 wx rx3 c2 at1 sc1 s2 at2 sc2",
                "s3 rx1 wx s1 at1 rx3 c2 sc1 s2 at2 sc2",
                "s3 rx1 wx s1 rx3 at1 c2 sc1 s2 at2 sc2",
                "s3 rx1 wx s1 rx3 c2 at1 sc1 s2 at2 sc2",
                "s3 rx1 wx rx3 s1 at1 c2 sc1 s2 at2 sc2",
                "s3 rx1 wx rx3 s1 c2 at1 sc1 s2 at2 sc2",
                "s3 rx1 wx rx3 c2 s1 at1 sc1 s2 at2 sc2",
                "s1 at1 sc1 s2 at2 s3 rx1 wx rx3 c2 sc2",
                "s1 at1 sc1 s2 at2 s3 rx1 wx sc2 rx3 c2",
                "s1 at1 sc1 s2 at2 sc2 s3 rx1 wx rx3 c2"
        );

        ConcurrencyTestUtils.executePermutations(
                executions,
                operations,
                sessions,
                this::setupAlterTable1,
                this::cleanupAlterTables1 );
    }

}
