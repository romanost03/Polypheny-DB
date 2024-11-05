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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Based on code taken from https://github.com/dstibrany/LockManager
// Cycle detection improved to return on first cycle containing a specific transaction
public class WaitForGraph {

    private final ConcurrentMap<TransactionImpl, Set<TransactionImpl>> adjacencyList = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock concurrencyLock = new ReentrantReadWriteLock();
    private final Lock sharedLock = concurrencyLock.readLock();
    private final Lock exclusiveLock = concurrencyLock.readLock();


    public void add( TransactionImpl predecessor, Set<TransactionImpl> successors ) {
        sharedLock.lock();
        try {
            Set<TransactionImpl> currentSuccessors = adjacencyList.getOrDefault( predecessor, new ConcurrentSkipListSet<>() );
            currentSuccessors.addAll( successors );
            adjacencyList.put( predecessor, currentSuccessors );
        } finally {
            sharedLock.unlock();
        }
    }


    public void remove( TransactionImpl transaction ) {
        sharedLock.lock();
        try {
            adjacencyList.remove( transaction );
            adjacencyList.forEach( ( predecessor, successors ) -> {
                if ( successors != null ) {
                    successors.remove( transaction );
                }
            } );
        } finally {
            sharedLock.unlock();
        }
    }


    public boolean isMemberOfCycle( TransactionImpl transaction ) {
        Set<TransactionImpl> visited = new HashSet<>();
        Set<TransactionImpl> recursionStack = new HashSet<>();
        exclusiveLock.lock();
        try {
            return runDepthFirstSearch( transaction, visited, recursionStack );
        } finally {
            exclusiveLock.unlock();
        }
    }


    private boolean runDepthFirstSearch( TransactionImpl currentTransaction, Set<TransactionImpl> visited, Set<TransactionImpl> recursionStack ) {
        if ( recursionStack.contains( currentTransaction ) ) {
            return true;
        }
        if ( visited.contains( currentTransaction ) ) {
            return false;
        }
        visited.add( currentTransaction );
        recursionStack.add( currentTransaction );
        for ( TransactionImpl neighbor : adjacencyList.get( currentTransaction ) ) {
            if ( runDepthFirstSearch( neighbor, visited, recursionStack ) ) {
                return true;
            }
        }
        recursionStack.remove( currentTransaction );
        return false;
    }

}
