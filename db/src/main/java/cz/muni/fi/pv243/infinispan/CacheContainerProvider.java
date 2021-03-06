package cz.muni.fi.pv243.infinispan;

import cz.muni.fi.pv243.dao.DemoDaoImpl;
import cz.muni.fi.pv243.model.Demo;
import lombok.extern.slf4j.Slf4j;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.util.concurrent.IsolationLevel;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Implementation of {@link CacheContainerProvider} creating a programmatically configured DefaultCacheManager.
 *
 * @author Marian Camak
 */
@ApplicationScoped
@Slf4j
public class CacheContainerProvider {

	private BasicCacheContainer manager;

	@Inject
	private CacheOperationLogger cacheLogger;

	public BasicCacheContainer getCacheContainer() {
		if (manager == null) {
			GlobalConfiguration glob = GlobalConfigurationBuilder.defaultClusteredBuilder()
					.transport().defaultTransport()
					.clusterName("TracksAppCluster")
					.globalJmxStatistics().enable()
					.jmxDomain("org.infinispan.demo")
//					.addProperty("configurationFile", "jgroups-tcp.xml")
					.build();

			Configuration defaultConfig = new ConfigurationBuilder()
					.transaction().transactionMode(TransactionMode.TRANSACTIONAL)
					.build();

			@SuppressWarnings("deprecation")
			Configuration demoCacheConfig = new ConfigurationBuilder()
					.jmxStatistics().enable()
					.clustering()
						.cacheMode(CacheMode.REPL_SYNC)
						.sync()
					.transaction()
						.transactionMode(TransactionMode.TRANSACTIONAL)
						.autoCommit(false)
						.lockingMode(LockingMode.OPTIMISTIC)
						.transactionManagerLookup(new GenericTransactionManagerLookup())
						.locking().isolationLevel(IsolationLevel.REPEATABLE_READ)
						.supportsConcurrentUpdates(true)
					.eviction()
						.maxEntries(10)
						.strategy(EvictionStrategy.LRU)
					.persistence()
						.passivation(true)
						.addSingleFileStore()
						.purgeOnStartup(true)
					.indexing()
						.enable()
						.addIndexedEntity(Demo.class)
						.addProperty("default.directory_provider", "ram")
					.build();

			manager = new DefaultCacheManager(glob, defaultConfig);
			((DefaultCacheManager) manager).defineConfiguration(DemoDaoImpl.DEMO_CACHE_NAME, demoCacheConfig);
			manager.start();

			cacheLogger.init();
			log.info("Cache container configured.");
		}
		return manager;
	}

	@PreDestroy
	public void cleanUp() {
		manager.stop();
		manager = null;
	}
}
