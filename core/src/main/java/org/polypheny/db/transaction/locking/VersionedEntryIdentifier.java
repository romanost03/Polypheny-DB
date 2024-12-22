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

package org.polypheny.db.transaction.locking;

import java.util.Objects;
import lombok.Getter;

@Getter
public class VersionedEntryIdentifier {
    private final long entryIdentifier;
    private final long version;

    public VersionedEntryIdentifier(long entryIdentifier, long version) {
        this.entryIdentifier = entryIdentifier;
        this.version = version;
    }

    public VersionedEntryIdentifier(long entryIdentifier) {
        this.entryIdentifier = entryIdentifier;
        this.version = 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryIdentifier, version);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        VersionedEntryIdentifier that = (VersionedEntryIdentifier) other;
        return entryIdentifier == that.entryIdentifier && version == that.version;
    }
}
