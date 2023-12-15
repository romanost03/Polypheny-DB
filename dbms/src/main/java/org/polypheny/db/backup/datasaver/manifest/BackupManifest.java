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

package org.polypheny.db.backup.datasaver.manifest;


import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * This class represents the manifest file that is created during a backup.
 */
@Getter @Setter
public class BackupManifest {
    private List<EntityInfo> entityInfos;
    private String overallChecksum;
    private Date backupDate;


    /**
     * The manifest contains information about the saved data from the backup
     * @param entityInfos list of entityInfos - Information about each saved entity in the backup
     * @param overallChecksum checksum of the whole backup (not implemented yet)
     * @param backupDate date when the backup was created
     */
    public BackupManifest( List<EntityInfo> entityInfos, String overallChecksum, Date backupDate ) {
        this.entityInfos = entityInfos;
        this.overallChecksum = overallChecksum;
        this.backupDate = backupDate;
    }

}
