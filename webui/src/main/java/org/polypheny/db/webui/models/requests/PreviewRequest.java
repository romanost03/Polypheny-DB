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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.polypheny.db.catalog.entity.LogicalAdapter.AdapterType;
import java.util.Map;

@Data
public class PreviewRequest {

    @JsonProperty
    public String adapterName;

    @JsonProperty
    public AdapterType adapterType;

    @JsonProperty
    public Map<String, String> settings;

    @JsonProperty
    public int limit;

    @JsonProperty
    public String uniqueName;

    public PreviewRequest() { }

    public PreviewRequest(
            @JsonProperty("adapterName") String adapterName,
            @JsonProperty("adapterType") AdapterType adapterType,
            @JsonProperty("settings") Map<String,String> settings,
            @JsonProperty("limit") int rowLimit,
            @JsonProperty("uniqueName") String uniqueName ) {
        this.adapterName = adapterName;
        this.adapterType = adapterType;
        this.settings = settings;
        this.limit = rowLimit;
        this.uniqueName = uniqueName;
    }

}
