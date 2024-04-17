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

package org.polypheny.db.algebra.polyalg.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.ParamType;
import org.polypheny.db.algebra.polyalg.PolyAlgUtils;

public class ListArg<E extends PolyAlgArg> implements PolyAlgArg {

    public static final ListArg<AnyArg> EMPTY = new ListArg<>( List.of(), List.of() );

    @Getter
    private final List<E> args;
    @Getter
    private final List<String> aliases;
    private final boolean unpackValues;
    private final boolean containsListArg; // if a list is inside a list, the lists need brackets to be unambiguous


    public ListArg( List<E> args, List<String> aliases, boolean unpackValues ) {
        this.args = args;
        this.aliases = aliases;
        this.unpackValues = unpackValues;
        this.containsListArg = !args.isEmpty() && args.get( 0 ) instanceof ListArg<?>;
    }


    public ListArg( List<E> args ) {
        this( args, (List<String>) null, false );
    }


    public ListArg( List<E> args, List<String> aliases ) {
        this( args, aliases, false );
    }


    public <T> ListArg( List<T> rawArgs, Function<T, E> converter ) {
        this( rawArgs, converter, null, false );
    }


    public <T> ListArg( List<T> rawArgs, Function<T, E> converter, boolean unpackValues ) {
        this( rawArgs, converter, null, unpackValues );
    }


    public <T> ListArg( List<T> rawArgs, Function<T, E> converter, List<String> aliases ) {
        this( rawArgs.stream().map( converter ).toList(), aliases, false );
    }


    public <T> ListArg( List<T> rawArgs, Function<T, E> converter, List<String> aliases, boolean unpackValues ) {
        this( rawArgs.stream().map( converter ).toList(), aliases, unpackValues );
    }


    @Override
    public ParamType getType() {
        if ( args.isEmpty() ) {
            return ParamType.LIST;
        }
        return args.get( 0 ).getType();
    }


    @Override
    public String toPolyAlg( AlgNode context, @NonNull List<String> inputFieldNames ) {
        return toPolyAlg( context, inputFieldNames, false );
    }


    public String toPolyAlg( AlgNode context, @NonNull List<String> inputFieldNames, boolean enforceBrackets ) {
        List<String> strArgs = containsListArg ?
                args.stream().map( a -> ((ListArg<?>) a).toPolyAlg( context, inputFieldNames, true ) ).toList() :
                args.stream().map( a -> a.toPolyAlg( context, inputFieldNames ) ).toList();

        if ( aliases != null ) {
            strArgs = PolyAlgUtils.appendAliases( strArgs, aliases );
        }
        if ( containsListArg || enforceBrackets ) {
            return PolyAlgUtils.joinMultiValuedWithBrackets( strArgs );
        }
        return PolyAlgUtils.joinMultiValued( strArgs, unpackValues );
    }

    @Override
    public ObjectNode serialize( AlgNode context, @NonNull List<String> inputFieldNames, ObjectMapper mapper ) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode argsNode = mapper.createArrayNode();

        String type = containsListArg ? ParamType.LIST.name() : getType().name();
        for (int i = 0; i < args.size(); i++) {
            E element = this.args.get( i );
            ObjectNode elementNode = element.serializeWrapped( context, inputFieldNames, mapper );

            if (aliases != null) {
                ObjectNode innerArg = (ObjectNode) elementNode.get( "value" );
                if (!innerArg.has( "alias" )) {
                    innerArg.put( "alias", aliases.get( i ) );
                }
            }
            argsNode.add( elementNode );
        }
        node.put("innerType", type); // redundant, but might be useful since all children must have the same type
        node.set("args", argsNode);

        return node;
    }


    @Override
    public ObjectNode serializeWrapped( AlgNode context, @NonNull List<String> inputFieldNames, ObjectMapper mapper ) {
        ObjectNode node = PolyAlgArg.super.serializeWrapped( context, inputFieldNames, mapper );

        // overwrite type, since on this level we are not interested in the inner type
        return node.put("type", ParamType.LIST.name());
    }


    public <T> List<T> map( Function<E, T> mapper ) {
        return args.stream().map( mapper ).toList();
    }


    public boolean isEmpty() {
        return args.isEmpty();
    }

}
