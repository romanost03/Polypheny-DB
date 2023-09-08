/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.mqtt;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.polypheny.db.TestHelper;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;

public class MqttStreamProcessorTest {

    @BeforeClass
    public static void init() {
        TestHelper testHelper = TestHelper.getInstance();

    }

    @Test
    public void filterTestForSingleNumberMessage() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":10}";

        MqttMessage mqttMessage = new MqttMessage( "10", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue(streamProcessor.applyFilter());

    }

    @Test
    public void filterTestForSingleNumberMessage2() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":10}";

        MqttMessage mqttMessage1 = new MqttMessage( "15", "button/battery" );
        MqttStreamProcessor streamProcessor1 = new MqttStreamProcessor( mqttMessage1, filterQuery, st );
        assertFalse(streamProcessor1.applyFilter());

    }


    @Test
    public void filterTestForSingleStringMessage1() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":\"shouldMatch\"}";

        MqttMessage mqttMessage2 = new MqttMessage( "shouldMatch", "button/battery" );
        MqttStreamProcessor streamProcessor2 = new MqttStreamProcessor( mqttMessage2, filterQuery, st );
        assertTrue(streamProcessor2.applyFilter());

    }


    @Test
    public void filterTestForSingleStringMessage2() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":\"shouldNot\"}";
        MqttMessage mqttMessage2 = new MqttMessage( "shouldNotMatch", "button/battery" );
        MqttStreamProcessor streamProcessor2 = new MqttStreamProcessor( mqttMessage2, filterQuery, st );
        assertFalse(streamProcessor2.applyFilter());
    }


    @Test
    public void filterTestForArrayMessage() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":10}";
        MqttMessage mqttMessage = new MqttMessage( "[10]", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter() );
    }


    @Test
    public void filterTestForArrayMessage2() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":[10]}";
        MqttMessage mqttMessage = new MqttMessage( "[10]", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter() );
    }


    @Test
    public void filterTestForArrayMessageFalse() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":10}";
        MqttMessage mqttMessage = new MqttMessage( "[15, 14]", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertFalse( streamProcessor.applyFilter());
    }

    @Test
    public void filterTestForBooleanMessageTrue() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":true}";
        MqttMessage mqttMessage = new MqttMessage( "true", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertFalse( streamProcessor.applyFilter());
    }
    @Test
    public void filterTestForBooleanMessageFalse() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"$$ROOT\":true}";
        MqttMessage mqttMessage = new MqttMessage( "false", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertFalse( streamProcessor.applyFilter());
    }


    @Test
    public void filterTestForJsonNumberMessage() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"count\":10}";
        MqttMessage mqttMessage = new MqttMessage( "{\"count\":10}", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter());
    }


    @Test
    public void filterTestForJsonArrayMessage() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"array\":10}";
        MqttMessage mqttMessage = new MqttMessage( "{\"array\":[10]}", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter());
    }


    @Test
    public void filterTestForJsonStringMessage() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"content\":\"online\"}";
        MqttMessage mqttMessage = new MqttMessage( "{\"content\":\"online\"}", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter());
    }

    @Test
    public void filterTestForJsonStringMessage2() {
        Transaction transaction = TestHelper.getInstance().getTransaction();
        Statement st = transaction.createStatement();
        String filterQuery = "{\"content\":\"online\"}";
        MqttMessage mqttMessage = new MqttMessage( "{\"content\":\"online\"}", "button/battery" );
        MqttStreamProcessor streamProcessor = new MqttStreamProcessor( mqttMessage, filterQuery, st );
        assertTrue( streamProcessor.applyFilter());
    }


}
