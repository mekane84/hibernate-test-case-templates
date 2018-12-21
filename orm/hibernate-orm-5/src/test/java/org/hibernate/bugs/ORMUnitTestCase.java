/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.bugs;

import entities.NonUIPerson;
import entities.Person;
import entities.UIPerson;
import entities.Works;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Second level caching fails for joined subclass inheritance structure
 */
public class ORMUnitTestCase extends BaseCoreFunctionalTestCase {

	// Add your entities here.
	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] {
				Person.class,
				UIPerson.class,
				NonUIPerson.class,
				Works.class
		};
	}

	// If you use *.hbm.xml mappings, instead of annotations, add the mappings here.
	@Override
	protected String[] getMappings() {
		return new String[] {
				"Person.hbm.xml",
				"Works.hbm.xml"
		};
	}
	// If those mappings reside somewhere other than resources/org/hibernate/test, change this.
	@Override
	protected String getBaseForMappings() {
		return "";
	}

	// Add in any settings that are specific to your test.  See resources/hibernate.properties for the defaults.
	@Override
	protected void configure(Configuration configuration) {
		super.configure( configuration );

		configuration.setProperty( AvailableSettings.SHOW_SQL, Boolean.TRUE.toString() );
		configuration.setProperty( AvailableSettings.FORMAT_SQL, Boolean.TRUE.toString() );
		configuration.setProperty( AvailableSettings.GENERATE_STATISTICS, "true" );
	}

	// This test works, similar to the failed test, "control" like in science
	@Test
	public void thisTestWorks() {
		// BaseCoreFunctionalTestCase automatically creates the SessionFactory and provides the Session.

		Session s = openSession();
		Transaction tx = s.beginTransaction();
		// Do stuff...

		String regionName = "entities.Works";

		// sanity check
		CacheRegionStatistics cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
		Assert.assertEquals("Cache put should be 0", 0, cacheRegionStatistics.getPutCount());

		// create object save it to DB
		Works works1 = new Works();
		works1.setOid(1L);
		s.save(works1);

		// close session
		tx.commit();

		// should see cache put in 2nd level cache
		cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
		Assert.assertEquals("Cache put should be 1", 1, cacheRegionStatistics.getPutCount());

		s.close();

		// open new session
		s = openSession();
		tx = s.beginTransaction();


		Works works2 = s.get(Works.class, 1L);
		System.out.println("got works2.oid: " + works2.getOid());

		cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
		System.out.println("cache puts: " + cacheRegionStatistics.getPutCount());
		Assert.assertEquals("Cache puts should be 1", 1, cacheRegionStatistics.getPutCount());
		Assert.assertEquals("Cache hits should be 1", 1, cacheRegionStatistics.getHitCount());

		tx.commit();
		s.close();
	}


	// This test FAILS because it is using joined subclass for caching
	@Test
	public void thisTestBroken() {
		// BaseCoreFunctionalTestCase automatically creates the SessionFactory and provides the Session.
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		// Do stuff...

		String regionName = "entities.Person";

		// sanity check
		CacheRegionStatistics cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
		Assert.assertEquals("Cache put should be 0", 0, cacheRegionStatistics.getPutCount());

		Person person1 = new UIPerson();
		person1.setOid(1L);
		s.save(person1);
		System.out.println("person1.oid: " + person1.getOid());

		tx.commit();

		// this put test fails too but commenting out for now so we get to the hitCount test failing below
//		cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
//		Assert.assertEquals("Cache put should be 1", 1, cacheRegionStatistics.getPutCount());
//		Assert.assertEquals("Cache hit should be 0", 0, cacheRegionStatistics.getHitCount());

		// I think it's not doing a PUT here because hibernate doesn't think there is a cached object for the subclass object
		// since it's checking the region using the subclass name instead of super class name

		s.close();

		s = openSession();
		tx = s.beginTransaction();

		Person person2 = s.get(Person.class, 1L);
		System.out.println("got person2.oid: " + person2.getOid());

		cacheRegionStatistics = s.getSessionFactory().getStatistics().getCacheRegionStatistics(regionName);
		Assert.assertEquals("Cache hit should be 1", 1, cacheRegionStatistics.getHitCount());
		Assert.assertEquals("Cache put should be 1", 1, cacheRegionStatistics.getPutCount());

		tx.commit();
		s.close();
	}
}
