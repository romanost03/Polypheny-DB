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

package org.polypheny.db.algebra.enumerable.lpg;

import java.util.function.Predicate;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.convert.ConverterRule;
import org.polypheny.db.algebra.core.lpg.LpgValues;
import org.polypheny.db.algebra.enumerable.EnumerableConvention;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgValues;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.tools.AlgBuilderFactory;

public class EnumerableLpgValuesRule extends ConverterRule {

    public EnumerableLpgValuesRule( AlgBuilderFactory algBuilderFactory ) {
        super( LogicalLpgValues.class, (Predicate<AlgNode>) r -> true, Convention.NONE, EnumerableConvention.INSTANCE, algBuilderFactory, "EnumerableLpgValuesRule" );
    }


    @Override
    public AlgNode convert( AlgNode alg ) {
        LpgValues values = (LpgValues) alg;
        return EnumerableLpgValues.create(values);
    }

}
