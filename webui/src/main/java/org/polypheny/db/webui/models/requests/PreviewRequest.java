/*
 * Copyright 2019-2025 The Polypheny Project
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

package org.polypheny.db.webui.models.requests;

import org.polypheny.db.catalog.entity.LogicalAdapter.AdapterType;
import java.util.Map;

public class PreviewRequest {

    public String adapterName;
    public AdapterType adapterType;
    public Map<String, String> settings;
    public int rowLimit;

    public PreviewRequest(
            String adapterName,
            AdapterType adapterType,
            Map<String,String> settings,
            int rowLimit ) {
        this.adapterName = adapterName;
        this.adapterType = adapterType;
        this.settings = settings;
        this.rowLimit = rowLimit;
    }

}
