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

package org.polypheny.db.schemaDiscovery;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node implements AbstractNode {

    @Setter
    @Getter
    protected String type;
    @Setter
    @Getter
    protected String name;
    @Setter
    @Getter
    protected List<AbstractNode> children;
    @Setter
    @Getter
    protected Map<String, Object> properties;

    public Node(String type, String name) {
        this.type = type;
        this.name = name;
        this.children = new ArrayList<>();
        this.properties = new HashMap<>();
    }


    public void addChild(AbstractNode node) {
        children.add(node);
    }


    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }


}
