/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.lifecycle.glue;

import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.lifecycle.api.ExternalBusService;
import org.killbill.billing.lifecycle.bus.DefaultBusService;
import org.killbill.billing.lifecycle.bus.DefaultExternalBusService;
import org.killbill.billing.lifecycle.bus.ExternalPersistentBusConfig;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.bus.InMemoryPersistentBus;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class BusModule extends AbstractModule {

    public static final String EXTERNAL_BUS_NAMED = "externalBus";

    private final BusType type;
    private final boolean isExternal;
    private final KillbillConfigSource configSource;

    public BusModule(final BusType type, final boolean isExternal, final KillbillConfigSource configSource) {
        this.type = type;
        this.isExternal = isExternal;
        this.configSource = configSource;
    }

    public enum BusType {
        MEMORY,
        PERSISTENT
    }

    @Override
    protected void configure() {
        if (isExternal) {
            bind(ExternalBusService.class).to(DefaultExternalBusService.class).asEagerSingleton();
        } else {
            bind(BusService.class).to(DefaultBusService.class).asEagerSingleton();
        }
        switch (type) {
            case MEMORY:
                configureInMemoryEventBus();
                break;
            case PERSISTENT:
                configurePersistentEventBus();
                break;
            default:
                throw new RuntimeException("Unrecognized EventBus type " + type);
        }
    }

    protected void configurePersistentEventBus() {
        final SkifePersistentBusConfigSource skifePersistentBusConfigSource = new SkifePersistentBusConfigSource();
        final PersistentBusConfig busConfig = new ConfigurationObjectFactory(skifePersistentBusConfigSource).buildWithReplacements(PersistentBusConfig.class,
                                                                                                                                   ImmutableMap.<String, String>of("instanceName", isExternal ? ExternalPersistentBusConfig.EXTERNAL_BUS_NAME : ExternalPersistentBusConfig.MAIN_BUS_NAME));

        final PersistentBusProvider busProvider = new PersistentBusProvider(busConfig);
        if (isExternal) {
            bind(PersistentBusProvider.class).annotatedWith(Names.named(BusModule.EXTERNAL_BUS_NAMED)).toInstance(busProvider);
            bind(PersistentBus.class).annotatedWith(Names.named(BusModule.EXTERNAL_BUS_NAMED)).toProvider(Key.get(PersistentBusProvider.class, Names.named(BusModule.EXTERNAL_BUS_NAMED))).asEagerSingleton();
        } else {
            bind(PersistentBusProvider.class).toInstance(busProvider);
            bind(PersistentBus.class).toProvider(PersistentBusProvider.class).asEagerSingleton();
        }
    }

    private void configureInMemoryEventBus() {
        bind(PersistentBus.class).to(InMemoryPersistentBus.class).asEagerSingleton();
    }

    private final class SkifePersistentBusConfigSource implements ConfigSource {

        @Override
        public String getString(final String propertyName) {
            return configSource.getString(propertyName);
        }
    }
}
