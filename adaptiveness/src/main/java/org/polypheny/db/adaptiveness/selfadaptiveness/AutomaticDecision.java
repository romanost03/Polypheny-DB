/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.adaptiveness.selfadaptiveness;

import java.sql.Timestamp;
import lombok.Getter;
import lombok.Setter;
import org.polypheny.db.adaptiveness.selfadaptiveness.SelfAdaptiveUtil.AdaptiveKind;

/**
 * AutomaticDecision is used if the trigger is automatically through the workload monitoring.
 */
@Getter
public class AutomaticDecision<T> extends Decision {

    private final Class<T> clazz;
    private final T selected;
    private final String key;
    @Setter
    private Action bestAction;

    public AutomaticDecision( Timestamp timestamp, AdaptiveKind adaptiveKind, Class<T> clazz, T selected, String key ) {
        super( timestamp, adaptiveKind );
        this.clazz = clazz;
        this.selected = selected;
        this.key = key;

    }

}
