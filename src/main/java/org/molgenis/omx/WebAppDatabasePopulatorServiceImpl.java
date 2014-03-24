package org.molgenis.omx;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.molgenis.data.DataService;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.dataexplorer.controller.DataExplorerController;
import org.molgenis.framework.db.WebAppDatabasePopulatorService;
import org.molgenis.genomebrowser.controller.GenomebrowserController;
import org.molgenis.omx.auth.GroupAuthority;
import org.molgenis.omx.auth.MolgenisGroup;
import org.molgenis.omx.auth.MolgenisGroupMember;
import org.molgenis.omx.auth.MolgenisUser;
import org.molgenis.omx.controller.HomeController;
import org.molgenis.omx.core.RuntimeProperty;
import org.molgenis.security.account.AccountService;
import org.molgenis.security.core.utils.SecurityUtils;
import org.molgenis.security.runas.RunAsSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//import org.molgenis.genomebrowser.controller.GenomebrowserController;

@Service
public class WebAppDatabasePopulatorServiceImpl implements WebAppDatabasePopulatorService
{
	private static final String USERNAME_ADMIN = "admin";
	private static final String USERNAME_USER = "user";

	private final DataService dataService;

	@Value("${admin.password:@null}")
	private String adminPassword;
	@Value("${admin.email:molgenis+admin@gmail.com}")
	private String adminEmail;
	@Value("${user.password:@null}")
	private String userPassword;
	@Value("${user.email:molgenis+user@gmail.com}")
	private String userEmail;

	@Autowired
	public WebAppDatabasePopulatorServiceImpl(DataService dataService)
	{
		if (dataService == null) throw new IllegalArgumentException("DataService is null");
		this.dataService = dataService;
	}

	@Override
	@Transactional
	@RunAsSystem
	public void populateDatabase()
	{
		if (adminPassword == null) throw new RuntimeException(
				"please configure the admin.password property in your molgenis-server.properties");
		if (userPassword == null) throw new RuntimeException(
				"please configure the user.password property in your molgenis-server.properties");

		String firstName = "John";
		String lastName = "Doe";

		MolgenisUser userAdmin = new MolgenisUser();
		userAdmin.setUsername(USERNAME_ADMIN);
		userAdmin.setPassword(new BCryptPasswordEncoder().encode(adminPassword));
		userAdmin.setEmail(adminEmail);
		userAdmin.setFirstName(firstName);
		userAdmin.setLastName(lastName);
		userAdmin.setActive(true);
		userAdmin.setSuperuser(true);
		userAdmin.setFirstName(USERNAME_ADMIN);
		userAdmin.setLastName(USERNAME_ADMIN);
		dataService.add(MolgenisUser.ENTITY_NAME, userAdmin);

		MolgenisUser userUser = new MolgenisUser();
		userUser.setUsername(USERNAME_USER);
		userUser.setPassword(new BCryptPasswordEncoder().encode(userPassword));
		userUser.setEmail(userEmail);
		userUser.setFirstName(firstName);
		userUser.setLastName(lastName);
		userUser.setActive(true);
		userUser.setSuperuser(false);
		userUser.setFirstName(USERNAME_USER);
		userUser.setLastName(USERNAME_USER);
		dataService.add(MolgenisUser.ENTITY_NAME, userUser);

		MolgenisGroup usersGroup = new MolgenisGroup();
		usersGroup.setName(AccountService.ALL_USER_GROUP);
		dataService.add(MolgenisGroup.ENTITY_NAME, usersGroup);
		usersGroup.setName(AccountService.ALL_USER_GROUP);

		GroupAuthority usersGroupHomeAuthority = new GroupAuthority();
		usersGroupHomeAuthority.setMolgenisGroup(usersGroup);
		usersGroupHomeAuthority.setRole(SecurityUtils.AUTHORITY_PLUGIN_READ_PREFIX + HomeController.ID.toUpperCase());
		dataService.add(GroupAuthority.ENTITY_NAME, usersGroupHomeAuthority);

		MolgenisGroupMember molgenisGroupMember1 = new MolgenisGroupMember();
		molgenisGroupMember1.setMolgenisGroup(usersGroup);
		molgenisGroupMember1.setMolgenisUser(userUser);
		dataService.add(MolgenisGroupMember.ENTITY_NAME, molgenisGroupMember1);

		for (String entityName : dataService.getEntityNames())
		{
			GroupAuthority entityAuthority = new GroupAuthority();
			entityAuthority.setMolgenisGroup(usersGroup);
			entityAuthority.setRole(SecurityUtils.AUTHORITY_ENTITY_READ_PREFIX + entityName.toUpperCase());
			dataService.add(GroupAuthority.ENTITY_NAME, entityAuthority);
		}

		// Genomebrowser stuff
		Map<String, String> runtimePropertyMap = new HashMap<String, String>();

		runtimePropertyMap.put(GenomebrowserController.INITLOCATION,
				"chr:'3', viewStart:48560000,viewEnd:48600000,cookieKey:'human'");
		runtimePropertyMap.put(GenomebrowserController.COORDSYSTEM,
				"{speciesName: 'Human',taxon: 9606,auth: 'GRCh',version: '37'}");
		runtimePropertyMap
				.put(GenomebrowserController.CHAINS,
						"{hg18ToHg19: new Chainset('http://www.derkholm.net:8080/das/hg18ToHg19/', 'NCBI36', 'GRCh37',{speciesName: 'Human',taxon: 9606,auth: 'NCBI',version: 36})}");
		// for use of the demo dataset add to
		// SOURCES:",{name:'molgenis mutations',uri:'http://localhost:8080/das/molgenis/',desc:'Default from WebAppDatabasePopulatorService'}"
		runtimePropertyMap
				.put(GenomebrowserController.SOURCES,
						"[{name:'Genome',uri:'http://www.derkholm.net:8080/das/hg19comp/',desc:'Human reference genome build GRCh37',tier_type:'sequence',provides_entrypoints: true},{name:'Genes',desc:'Gene structures from Ensembl 59 (GENCODE 4)',uri:'http://www.derkholm.net:8080/das/hsa_59_37d/',collapseSuperGroups:true,provides_karyotype:true,provides_search:true}]");
		runtimePropertyMap
				.put(GenomebrowserController.BROWSERLINKS,
						"{Ensembl: 'http://www.ensembl.org/Homo_sapiens/Location/View?r=${chr}:${start}-${end}',UCSC: 'http://genome.ucsc.edu/cgi-bin/hgTracks?db=hg19&position=chr${chr}:${start}-${end}',Sequence: 'http://www.derkholm.net:8080/das/hg19comp/sequence?segment=${chr}:${start},${end}'}");
		runtimePropertyMap.put(GenomebrowserController.SEARCHENDPOINT,
				"new DASSource('http://www.derkholm.net:8080/das/hsa_59_37d/')");
		runtimePropertyMap.put(GenomebrowserController.KARYOTYPEENDPOINT,
				"new DASSource('http://www.derkholm.net:8080/das/hsa_59_37d/')");

		// Charts include/exclude charts
		runtimePropertyMap.put(DataExplorerController.KEY_MOD_AGGREGATES, String.valueOf(true));
		runtimePropertyMap.put(DataExplorerController.KEY_MOD_CHARTS, String.valueOf(true));
		runtimePropertyMap.put(DataExplorerController.KEY_MOD_DATA, String.valueOf(true));
        runtimePropertyMap.put("RuntimeProperty_app.href.logo","/img/logo_palga.png");


            // Annotators include files/tools
		String molgenisHomeDir = System.getProperty("molgenis.home");

		if (molgenisHomeDir == null)
		{
			throw new IllegalArgumentException("missing required java system property 'molgenis.home'");
		}

		for (Entry<String, String> entry : runtimePropertyMap.entrySet())
		{
			RuntimeProperty runtimeProperty = new RuntimeProperty();
			String propertyKey = entry.getKey();
			runtimeProperty.setIdentifier(RuntimeProperty.class.getSimpleName() + '_' + propertyKey);
			runtimeProperty.setName(propertyKey);
			runtimeProperty.setValue(entry.getValue());
			dataService.add(RuntimeProperty.ENTITY_NAME, runtimeProperty);
		}
	}

	@Override
	@Transactional
	@RunAsSystem
	public boolean isDatabasePopulated()
	{
		return dataService.count(MolgenisUser.ENTITY_NAME, new QueryImpl()) > 0;
	}
}