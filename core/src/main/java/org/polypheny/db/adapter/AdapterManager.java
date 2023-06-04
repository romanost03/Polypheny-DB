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

package org.polypheny.db.adapter;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.adapter.DeployMode.DeploySetting;
import org.polypheny.db.adapter.annotations.AdapterProperties;
import org.polypheny.db.adapter.java.AdapterTemplate;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.CatalogAdapter.AdapterType;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.config.ConfigDocker;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.util.Pair;

public class AdapterManager {

    public static Expression ADAPTER_MANAGER_EXPRESSION = Expressions.call( AdapterManager.class, "getInstance" );

    private static final Map<Pair<String, AdapterType>, AdapterTemplate> REGISTER = new ConcurrentHashMap<>();

    private final Map<Long, Adapter<?>> adapterById = new HashMap<>();
    private final Map<String, Adapter<?>> adapterByName = new HashMap<>();


    private static final AdapterManager INSTANCE = new AdapterManager();


    public static AdapterManager getInstance() {
        return INSTANCE;
    }


    private AdapterManager() {
        // intentionally empty
    }


    public static void addAdapterDeploy( Class<?> clazz, String adapterName, Map<String, String> defaultSettings, Function4<Long, String, Map<String, String>, Adapter<?>> deployer ) {
        REGISTER.put( Pair.of( adapterName.toLowerCase(), AdapterTemplate.getAdapterType( clazz ) ), new AdapterTemplate( clazz, adapterName, defaultSettings, deployer ) );
    }


    public static void removeAdapterTemplate( Class<?> clazz, String adapterName ) {
        if ( Catalog.getInstance().getSnapshot().getAdapters().stream().anyMatch( a -> a.adapterName.equals( adapterName ) ) ) {
            throw new RuntimeException( "Adapter is still deployed!" );
        }
        REGISTER.remove( Pair.of( adapterName.toLowerCase(), AdapterTemplate.getAdapterType( clazz ) ) );
    }


    public static List<AdapterTemplate> getAdapters( AdapterType adapterType ) {
        return REGISTER.values().stream().filter( a -> a.adapterType == adapterType ).collect( Collectors.toList() );
    }


    public static AdapterTemplate getAdapterType( String name, AdapterType adapterType ) {
        return REGISTER.get( Pair.of( name.toLowerCase(), adapterType ) );
    }


    public Adapter<?> getAdapter( String uniqueName ) {
        uniqueName = uniqueName.toLowerCase();
        return adapterByName.get( uniqueName );
    }


    public Adapter<?> getAdapter( long id ) {
        return adapterById.get( id );
    }


    public ImmutableMap<String, Adapter<?>> getAdapters() {
        return ImmutableMap.copyOf( adapterByName );
    }


    public DataStore<?> getStore( String uniqueName ) {
        Adapter<?> adapter = getAdapter( uniqueName );
        if ( adapter instanceof DataStore ) {
            return (DataStore<?>) adapter;
        }
        return null;
    }


    public DataStore<?> getStore( long id ) {
        Adapter<?> adapter = getAdapter( id );
        if ( adapter instanceof DataStore ) {
            return (DataStore<?>) adapter;
        }
        return null;
    }


    public ImmutableMap<String, DataStore<?>> getStores() {
        Map<String, DataStore<?>> map = new HashMap<>();
        for ( Entry<String, Adapter<?>> entry : getAdapters().entrySet() ) {
            if ( entry.getValue() instanceof DataStore<?> ) {
                map.put( entry.getKey(), (DataStore<?>) entry.getValue() );
            }
        }
        return ImmutableMap.copyOf( map );
    }


    public DataSource<?> getSource( String uniqueName ) {
        Adapter<?> adapter = getAdapter( uniqueName );
        if ( adapter instanceof DataSource<?> ) {
            return (DataSource<?>) adapter;
        }
        return null;
    }


    public DataSource<?> getSource( long id ) {
        Adapter<?> adapter = getAdapter( id );
        if ( adapter instanceof DataSource<?> ) {
            return (DataSource<?>) adapter;
        }
        return null;
    }


    public ImmutableMap<String, DataSource<?>> getSources() {
        Map<String, DataSource<?>> map = new HashMap<>();
        for ( Entry<String, Adapter<?>> entry : getAdapters().entrySet() ) {
            if ( entry.getValue() instanceof DataSource<?> ) {
                map.put( entry.getKey(), (DataSource<?>) entry.getValue() );
            }
        }
        return ImmutableMap.copyOf( map );
    }


    public List<AdapterInformation> getAvailableAdapters( AdapterType adapterType ) {
        List<AdapterTemplate> adapterTemplates = getAdapters( adapterType );

        List<AdapterInformation> result = new LinkedList<>();

        for ( AdapterTemplate adapterTemplate : adapterTemplates ) {
            // Exclude abstract classes
            if ( !Modifier.isAbstract( adapterTemplate.getClazz().getModifiers() ) ) {
                Map<String, List<AbstractAdapterSetting>> settings = new HashMap<>();

                AdapterProperties properties = adapterTemplate.getClazz().getAnnotation( AdapterProperties.class );
                if ( properties == null ) {
                    throw new RuntimeException( adapterTemplate.getClazz().getSimpleName() + " does not annotate the adapter correctly" );
                }

                // Used to evaluate which mode is used when deploying the adapter
                settings.put(
                        "mode",
                        Collections.singletonList(
                                new AbstractAdapterSettingList(
                                        "mode",
                                        false,
                                        null,
                                        true,
                                        true,
                                        Collections.singletonList( "default" ),
                                        Collections.singletonList( DeploySetting.DEFAULT ),
                                        "default",
                                        0 ) ) );

                // Add empty list for each available mode
                Arrays.stream( properties.usedModes() ).forEach( mode -> settings.put( mode.getName(), new ArrayList<>() ) );

                // Add default which is used by all available modes
                settings.put( "default", new ArrayList<>() );

                // Merge annotated AdapterSettings into settings
                Map<String, List<AbstractAdapterSetting>> annotatedSettings = AbstractAdapterSetting.fromAnnotations( adapterTemplate.getClazz().getAnnotations(), adapterTemplate.getClazz().getAnnotation( AdapterProperties.class ) );
                settings.putAll( annotatedSettings );

                // If the adapter uses docker add the dynamic docker setting
                if ( settings.containsKey( "docker" ) ) {
                    settings
                            .get( "docker" )
                            .add( new BindableAbstractAdapterSettingsList<>(
                                    "instanceId",
                                    "DockerInstance",
                                    false,
                                    null,
                                    true,
                                    false,
                                    RuntimeConfig.DOCKER_INSTANCES.getList( ConfigDocker.class ).stream().filter( ConfigDocker::isDockerRunning ).collect( Collectors.toList() ),
                                    ConfigDocker::getAlias,
                                    ConfigDocker.class )
                                    .bind( RuntimeConfig.DOCKER_INSTANCES )
                                    .setDescription( "To configure additional Docker instances, use the Docker Config in the Config Manager." ) );
                }

                result.add( new AdapterInformation( properties.name(), properties.description(), adapterType, settings ) );
            }
        }

        return result;
    }


    public Adapter<?> addAdapter( String adapterName, String uniqueName, AdapterType adapterType, Map<String, String> settings ) {
        uniqueName = uniqueName.toLowerCase();
        if ( getAdapters().containsKey( uniqueName ) ) {
            throw new RuntimeException( "There is already an adapter with this unique name" );
        }
        if ( !settings.containsKey( "mode" ) ) {
            throw new RuntimeException( "The adapter does not specify a mode which is necessary." );
        }

        AdapterTemplate adapterTemplate = AdapterTemplate.fromString( adapterName, adapterType );

        long adapterId = Catalog.getInstance().addAdapter( uniqueName, adapterName, adapterType, settings );
        try {
            Adapter<?> adapter = adapterTemplate.getDeployer().get( adapterId, uniqueName, settings );
            adapterByName.put( adapter.getUniqueName(), adapter );
            adapterById.put( adapter.getAdapterId(), adapter );
            return adapter;

        } catch ( Exception e ) {
            Catalog.getInstance().deleteAdapter( adapterId );
            throw new RuntimeException( "Something went wrong while adding a new adapter", e );
        }
    }


    public void removeAdapter( long adapterId ) {
        Adapter<?> adapterInstance = getAdapter( adapterId );
        if ( adapterInstance == null ) {
            throw new RuntimeException( "Unknown adapter instance with id: " + adapterId );
        }
        CatalogAdapter catalogAdapter = Catalog.getInstance().getSnapshot().getAdapter( adapterId );

        // Check if the store has any placements
        List<AllocationEntity> placements = Catalog.getInstance().getSnapshot().alloc().getEntitiesOnAdapter( catalogAdapter.id ).orElseThrow( () -> new GenericRuntimeException( "There is still data placed on this data store" ) );
        if ( placements.size() != 0 ) {
            throw new RuntimeException( "There is still data placed on this data store" );
        }

        // Shutdown store
        adapterInstance.shutdownAndRemoveListeners();

        // Remove store from maps
        adapterById.remove( adapterInstance.getAdapterId() );
        adapterByName.remove( adapterInstance.getUniqueName() );

        // Delete store from catalog
        Catalog.getInstance().deleteAdapter( catalogAdapter.id );
    }


    /**
     * Restores adapters from catalog
     */
    public void restoreAdapters() {
        try {
            List<CatalogAdapter> adapters = Catalog.getInstance().getSnapshot().getAdapters();
            for ( CatalogAdapter adapter : adapters ) {
                Constructor<?> ctor = AdapterTemplate.fromString( adapter.adapterName, adapter.type ).getClazz().getConstructor( long.class, String.class, Map.class );
                Adapter<?> instance = (Adapter<?>) ctor.newInstance( adapter.id, adapter.uniqueName, adapter.settings );
                adapterByName.put( instance.getUniqueName(), instance );
                adapterById.put( instance.getAdapterId(), instance );
            }
        } catch ( NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e ) {
            throw new RuntimeException( "Something went wrong while restoring adapters from the catalog.", e );
        }
    }


    @AllArgsConstructor
    public static class AdapterInformation {

        public final String name;
        public final String description;
        public final AdapterType type;
        public final Map<String, List<AbstractAdapterSetting>> settings;


        public static JsonSerializer<AdapterInformation> getSerializer() {
            return ( src, typeOfSrc, context ) -> {
                JsonObject jsonStore = new JsonObject();
                jsonStore.addProperty( "name", src.name );
                jsonStore.addProperty( "description", src.description );
                jsonStore.addProperty( "type", src.type.name() );
                jsonStore.add( "adapterSettings", context.serialize( src.settings ) );
                return jsonStore;
            };
        }

    }


    @FunctionalInterface
    public interface Function4<P1, P2, P3, R> {

        R get( P1 p1, P2 p2, P3 p3 );

    }


}
