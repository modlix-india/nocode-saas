select id from security.security_v2_role where name = 'Client Manager' limit 1 into @v_r_1;
select id from security.security_v2_role where name = 'User Manager' limit 1 into @v_r_2;
select id from security.security_v2_role where name = 'Package Manager' limit 1 into @v_r_3;
select id from security.security_v2_role where name = 'Role Manager' limit 1 into @v_r_4;
select id from security.security_v2_role where name = 'Permission Manager' limit 1 into @v_r_5;
select id from security.security_v2_role where name = 'Client Type Manager' limit 1 into @v_r_6;
select id from security.security_v2_role where name = 'Client Update Manager' limit 1 into @v_r_7;
select id from security.security_v2_role where name = 'Application Manager' limit 1 into @v_r_8;
select id from security.security_v2_role where name = 'System Application Manager' limit 1 into @v_r_9;
select id from security.security_v2_role where name = 'Function Manager' limit 1 into @v_r_10;
select id from security.security_v2_role where name = 'Page Manager' limit 1 into @v_r_11;
select id from security.security_v2_role where name = 'Theme Manager' limit 1 into @v_r_12;
select id from security.security_v2_role where name = 'Personalization Manager' limit 1 into @v_r_13;
select id from security.security_v2_role where name = 'Files Manager' limit 1 into @v_r_14;
select id from security.security_v2_role where name = 'Data Manager' limit 1 into @v_r_15;
select id from security.security_v2_role where name = 'Data Connection Manager' limit 1 into @v_r_16;
select id from security.security_v2_role where name = 'Schema Manager' limit 1 into @v_r_17;
select id from security.security_v2_role where name = 'Workflow Manager' limit 1 into @v_r_18;
select id from security.security_v2_role where name = 'Template Manager' limit 1 into @v_r_19;
select id from security.security_v2_role where name = 'Actions Manager' limit 1 into @v_r_20;
select id from security.security_v2_role where name = 'Style Manager' limit 1 into @v_r_21;
select id from security.security_v2_role where name = 'Transport Manager' limit 1 into @v_r_22;
select id from security.security_v2_role where name = 'EventDefinition Manager' limit 1 into @v_r_23;
select id from security.security_v2_role where name = 'EventAction Manager' limit 1 into @v_r_24;
select id from security.security_v2_role where name = 'Super Admin' limit 1 into @v_r_25;
select id from security.security_v2_role where name = 'Integration Manager' limit 1 into @v_r_26;
select id from security.security_v2_role where name = 'Client CREATE' limit 1 into @v_r_32;
select id from security.security_v2_role where name = 'Client READ' limit 1 into @v_r_33;
select id from security.security_v2_role where name = 'Client UPDATE' limit 1 into @v_r_34;
select id from security.security_v2_role where name = 'Client DELETE' limit 1 into @v_r_35;
select id from security.security_v2_role where name = 'User CREATE' limit 1 into @v_r_37;
select id from security.security_v2_role where name = 'User READ' limit 1 into @v_r_38;
select id from security.security_v2_role where name = 'User UPDATE' limit 1 into @v_r_39;
select id from security.security_v2_role where name = 'User DELETE' limit 1 into @v_r_40;
select id from security.security_v2_role where name = 'Package CREATE' limit 1 into @v_r_43;
select id from security.security_v2_role where name = 'Package READ' limit 1 into @v_r_44;
select id from security.security_v2_role where name = 'Package UPDATE' limit 1 into @v_r_45;
select id from security.security_v2_role where name = 'Package DELETE' limit 1 into @v_r_46;
select id from security.security_v2_role where name = 'Role CREATE' limit 1 into @v_r_47;
select id from security.security_v2_role where name = 'Role READ' limit 1 into @v_r_48;
select id from security.security_v2_role where name = 'Role UPDATE' limit 1 into @v_r_49;
select id from security.security_v2_role where name = 'Role DELETE' limit 1 into @v_r_50;
select id from security.security_v2_role where name = 'Permission CREATE' limit 1 into @v_r_53;
select id from security.security_v2_role where name = 'Permission READ' limit 1 into @v_r_54;
select id from security.security_v2_role where name = 'Permission UPDATE' limit 1 into @v_r_55;
select id from security.security_v2_role where name = 'Permission DELETE' limit 1 into @v_r_56;
select id from security.security_v2_role where name = 'Client Type CREATE' limit 1 into @v_r_57;
select id from security.security_v2_role where name = 'Client Type READ' limit 1 into @v_r_58;
select id from security.security_v2_role where name = 'Client Type UPDATE' limit 1 into @v_r_59;
select id from security.security_v2_role where name = 'Client Type DELETE' limit 1 into @v_r_60;
select id from security.security_v2_role where name = 'Application CREATE' limit 1 into @v_r_61;
select id from security.security_v2_role where name = 'Application READ' limit 1 into @v_r_62;
select id from security.security_v2_role where name = 'Application UPDATE' limit 1 into @v_r_63;
select id from security.security_v2_role where name = 'Application DELETE' limit 1 into @v_r_64;
select id from security.security_v2_role where name = 'Function CREATE' limit 1 into @v_r_65;
select id from security.security_v2_role where name = 'Function READ' limit 1 into @v_r_66;
select id from security.security_v2_role where name = 'Function UPDATE' limit 1 into @v_r_67;
select id from security.security_v2_role where name = 'Function DELETE' limit 1 into @v_r_68;
select id from security.security_v2_role where name = 'Page CREATE' limit 1 into @v_r_69;
select id from security.security_v2_role where name = 'Page READ' limit 1 into @v_r_70;
select id from security.security_v2_role where name = 'Page UPDATE' limit 1 into @v_r_71;
select id from security.security_v2_role where name = 'Page DELETE' limit 1 into @v_r_72;
select id from security.security_v2_role where name = 'Theme CREATE' limit 1 into @v_r_73;
select id from security.security_v2_role where name = 'Theme READ' limit 1 into @v_r_74;
select id from security.security_v2_role where name = 'Theme UPDATE' limit 1 into @v_r_75;
select id from security.security_v2_role where name = 'Theme DELETE' limit 1 into @v_r_76;
select id from security.security_v2_role where name = 'Personalization CLEAR' limit 1 into @v_r_77;
select id from security.security_v2_role where name = 'STATIC Files PATH' limit 1 into @v_r_78;
select id from security.security_v2_role where name = 'SECURED Files PATH' limit 1 into @v_r_79;
select id from security.security_v2_role where name = 'Client Password Policy READ' limit 1 into @v_r_80;
select id from security.security_v2_role where name = 'Client Password Policy CREATE' limit 1 into @v_r_81;
select id from security.security_v2_role where name = 'Client Password Policy UPDATE' limit 1 into @v_r_82;
select id from security.security_v2_role where name = 'Client Password Policy DELETE' limit 1 into @v_r_83;
select id from security.security_v2_role where name = 'Storage CREATE' limit 1 into @v_r_84;
select id from security.security_v2_role where name = 'Storage READ' limit 1 into @v_r_85;
select id from security.security_v2_role where name = 'Storage UPDATE' limit 1 into @v_r_86;
select id from security.security_v2_role where name = 'Storage DELETE' limit 1 into @v_r_87;
select id from security.security_v2_role where name = 'Connection CREATE' limit 1 into @v_r_88;
select id from security.security_v2_role where name = 'Connection READ' limit 1 into @v_r_89;
select id from security.security_v2_role where name = 'Connection UPDATE' limit 1 into @v_r_90;
select id from security.security_v2_role where name = 'Connection DELETE' limit 1 into @v_r_91;
select id from security.security_v2_role where name = 'Schema CREATE' limit 1 into @v_r_92;
select id from security.security_v2_role where name = 'Schema READ' limit 1 into @v_r_93;
select id from security.security_v2_role where name = 'Schema UPDATE' limit 1 into @v_r_94;
select id from security.security_v2_role where name = 'Schema DELETE' limit 1 into @v_r_95;
select id from security.security_v2_role where name = 'Workflow CREATE' limit 1 into @v_r_96;
select id from security.security_v2_role where name = 'Workflow READ' limit 1 into @v_r_97;
select id from security.security_v2_role where name = 'Workflow UPDATE' limit 1 into @v_r_98;
select id from security.security_v2_role where name = 'Workflow DELETE' limit 1 into @v_r_99;
select id from security.security_v2_role where name = 'Template CREATE' limit 1 into @v_r_100;
select id from security.security_v2_role where name = 'Template READ' limit 1 into @v_r_101;
select id from security.security_v2_role where name = 'Template UPDATE' limit 1 into @v_r_102;
select id from security.security_v2_role where name = 'Template DELETE' limit 1 into @v_r_103;
select id from security.security_v2_role where name = 'Actions CREATE' limit 1 into @v_r_104;
select id from security.security_v2_role where name = 'Actions READ' limit 1 into @v_r_105;
select id from security.security_v2_role where name = 'Actions UPDATE' limit 1 into @v_r_106;
select id from security.security_v2_role where name = 'Actions DELETE' limit 1 into @v_r_107;
select id from security.security_v2_role where name = 'Style CREATE' limit 1 into @v_r_108;
select id from security.security_v2_role where name = 'Style READ' limit 1 into @v_r_109;
select id from security.security_v2_role where name = 'Style UPDATE' limit 1 into @v_r_110;
select id from security.security_v2_role where name = 'Style DELETE' limit 1 into @v_r_111;
select id from security.security_v2_role where name = 'Transport CREATE' limit 1 into @v_r_112;
select id from security.security_v2_role where name = 'Transport READ' limit 1 into @v_r_113;
select id from security.security_v2_role where name = 'Transport UPDATE' limit 1 into @v_r_114;
select id from security.security_v2_role where name = 'Transport DELETE' limit 1 into @v_r_115;
select id from security.security_v2_role where name = 'EventDefinition CREATE' limit 1 into @v_r_116;
select id from security.security_v2_role where name = 'EventDefinition READ' limit 1 into @v_r_117;
select id from security.security_v2_role where name = 'EventDefinition UPDATE' limit 1 into @v_r_118;
select id from security.security_v2_role where name = 'EventDefinition DELETE' limit 1 into @v_r_119;
select id from security.security_v2_role where name = 'EventAction CREATE' limit 1 into @v_r_120;
select id from security.security_v2_role where name = 'EventAction READ' limit 1 into @v_r_121;
select id from security.security_v2_role where name = 'EventAction UPDATE' limit 1 into @v_r_122;
select id from security.security_v2_role where name = 'EventAction DELETE' limit 1 into @v_r_123;
select id from security.security_v2_role where name = 'Integration CREATE' limit 1 into @v_r_124;
select id from security.security_v2_role where name = 'Integration READ' limit 1 into @v_r_125;
select id from security.security_v2_role where name = 'Integration UPDATE' limit 1 into @v_r_126;
select id from security.security_v2_role where name = 'Integration DELETE' limit 1 into @v_r_127;
select id from security.security_v2_role where name = 'Profile CREATE' limit 1 into @v_r_159;
select id from security.security_v2_role where name = 'Profile READ' limit 1 into @v_r_160;
select id from security.security_v2_role where name = 'Profile UPDATE' limit 1 into @v_r_161;
select id from security.security_v2_role where name = 'Profile DELETE' limit 1 into @v_r_162;
select id from security.security_v2_role where name = 'Profile Manager' limit 1 into @v_r_163;
select id from security.security_v2_role where name = 'Owner' limit 1 into @v_r_164;

select id from security.security_profile where name = 'Appbuilder Owner' into @v_profile_appbuilder_owner;

update security.security_profile set arrangement = JSON_OBJECT( "r164", JSON_OBJECT("order",3,"roleId",@v_r_164,"assignable",true),"g1", JSON_OBJECT("name","Security","order",2,"assignable",true,"subArrangements",JSON_OBJECT( "r163", JSON_OBJECT("order",0,"roleId",@v_r_163,"assignable",true,"subArrangements",JSON_OBJECT( "r160", JSON_OBJECT("order",1,"roleId",@v_r_160,"assignable",true),"r162", JSON_OBJECT("order",3,"roleId",@v_r_162,"assignable",true),"r159", JSON_OBJECT("order",0,"roleId",@v_r_159,"assignable",true),"r161", JSON_OBJECT("order",2,"roleId",@v_r_161,"assignable",true) )),"r2", JSON_OBJECT("order",1,"roleId",@v_r_2,"assignable",true,"subArrangements",JSON_OBJECT( "r39", JSON_OBJECT("order",2,"roleId",@v_r_39,"assignable",true),"r40", JSON_OBJECT("order",3,"roleId",@v_r_40,"assignable",true),"r37", JSON_OBJECT("order",0,"roleId",@v_r_37,"assignable",true),"r38", JSON_OBJECT("order",1,"roleId",@v_r_38,"assignable",true) )),"r22", JSON_OBJECT("order",6,"roleId",@v_r_22,"assignable",true,"subArrangements",JSON_OBJECT( "r114", JSON_OBJECT("order",2,"roleId",@v_r_114,"assignable",true),"r115", JSON_OBJECT("order",3,"roleId",@v_r_115,"assignable",true),"r112", JSON_OBJECT("order",0,"roleId",@v_r_112,"assignable",true),"r113", JSON_OBJECT("order",1,"roleId",@v_r_113,"assignable",true) )),"r14", JSON_OBJECT("order",3,"roleId",@v_r_14,"assignable",true,"subArrangements",JSON_OBJECT( "r78", JSON_OBJECT("roleId",@v_r_78,"assignable",true),"r79", JSON_OBJECT("roleId",@v_r_79,"assignable",true) )),"r4", JSON_OBJECT("order",5,"roleId",@v_r_4,"assignable",true,"subArrangements",JSON_OBJECT( "r49", JSON_OBJECT("order",2,"roleId",@v_r_49,"assignable",true),"r50", JSON_OBJECT("order",3,"roleId",@v_r_50,"assignable",true),"r47", JSON_OBJECT("order",0,"roleId",@v_r_47,"assignable",true),"r48", JSON_OBJECT("order",1,"roleId",@v_r_48,"assignable",true) )),"r1", JSON_OBJECT("order",2,"roleId",@v_r_1,"assignable",true,"subArrangements",JSON_OBJECT( "r33", JSON_OBJECT("order",1,"roleId",@v_r_33,"assignable",true),"r35", JSON_OBJECT("order",3,"roleId",@v_r_35,"assignable",true),"r34", JSON_OBJECT("order",2,"roleId",@v_r_34,"assignable",true),"r32", JSON_OBJECT("order",0,"roleId",@v_r_32,"assignable",true) )),"r5", JSON_OBJECT("order",4,"roleId",@v_r_5,"assignable",true,"subArrangements",JSON_OBJECT( "r54", JSON_OBJECT("order",1,"roleId",@v_r_54,"assignable",true),"r55", JSON_OBJECT("order",2,"roleId",@v_r_55,"assignable",true),"r56", JSON_OBJECT("order",3,"roleId",@v_r_56,"assignable",true),"r53", JSON_OBJECT("order",0,"roleId",@v_r_53,"assignable",true) )) )),"g2", JSON_OBJECT("name","Core","order",1,"assignable",true,"subArrangements",JSON_OBJECT( "r20", JSON_OBJECT("order",9,"roleId",@v_r_20,"assignable",true,"subArrangements",JSON_OBJECT( "r105", JSON_OBJECT("order",1,"roleId",@v_r_105,"assignable",true),"r107", JSON_OBJECT("order",3,"roleId",@v_r_107,"assignable",true),"r104", JSON_OBJECT("order",0,"roleId",@v_r_104,"assignable",true),"r106", JSON_OBJECT("order",2,"roleId",@v_r_106,"assignable",true) )),"r24", JSON_OBJECT("order",3,"roleId",@v_r_24,"assignable",true,"subArrangements",JSON_OBJECT( "r121", JSON_OBJECT("order",1,"roleId",@v_r_121,"assignable",true),"r120", JSON_OBJECT("order",0,"roleId",@v_r_120,"assignable",true),"r122", JSON_OBJECT("order",2,"roleId",@v_r_122,"assignable",true),"r123", JSON_OBJECT("order",3,"roleId",@v_r_123,"assignable",true) )),"r16", JSON_OBJECT("order",1,"roleId",@v_r_16,"assignable",true,"subArrangements",JSON_OBJECT( "r89", JSON_OBJECT("order",1,"roleId",@v_r_89,"assignable",true),"r91", JSON_OBJECT("order",3,"roleId",@v_r_91,"assignable",true),"r90", JSON_OBJECT("order",2,"roleId",@v_r_90,"assignable",true),"r88", JSON_OBJECT("order",0,"roleId",@v_r_88,"assignable",true) )),"r15", JSON_OBJECT("order",0,"roleId",@v_r_15,"assignable",true,"subArrangements",JSON_OBJECT( "r87", JSON_OBJECT("order",3,"roleId",@v_r_87,"assignable",true),"r84", JSON_OBJECT("order",0,"roleId",@v_r_84,"assignable",true),"r85", JSON_OBJECT("order",1,"roleId",@v_r_85,"assignable",true),"r86", JSON_OBJECT("order",2,"roleId",@v_r_86,"assignable",true) )),"r19", JSON_OBJECT("order",6,"roleId",@v_r_19,"assignable",true,"subArrangements",JSON_OBJECT( "r103", JSON_OBJECT("order",3,"roleId",@v_r_103,"assignable",true),"r101", JSON_OBJECT("order",1,"roleId",@v_r_101,"assignable",true),"r100", JSON_OBJECT("order",0,"roleId",@v_r_100,"assignable",true),"r102", JSON_OBJECT("order",2,"roleId",@v_r_102,"assignable",true) )),"r23", JSON_OBJECT("order",2,"roleId",@v_r_23,"assignable",true,"subArrangements",JSON_OBJECT( "r118", JSON_OBJECT("order",2,"roleId",@v_r_118,"assignable",true),"r119", JSON_OBJECT("order",3,"roleId",@v_r_119,"assignable",true),"r116", JSON_OBJECT("order",0,"roleId",@v_r_116,"assignable",true),"r117", JSON_OBJECT("order",1,"roleId",@v_r_117,"assignable",true) )),"r17", JSON_OBJECT("order",4,"roleId",@v_r_17,"assignable",true,"subArrangements",JSON_OBJECT( "r92", JSON_OBJECT("roleId",@v_r_92,"assignable",true),"r93", JSON_OBJECT("roleId",@v_r_93,"assignable",true),"r94", JSON_OBJECT("roleId",@v_r_94,"assignable",true),"r95", JSON_OBJECT("roleId",@v_r_95,"assignable",true) )),"r10", JSON_OBJECT("order",5,"roleId",@v_r_10,"assignable",true,"subArrangements",JSON_OBJECT( "r67", JSON_OBJECT("order",2,"roleId",@v_r_67,"assignable",true),"r66", JSON_OBJECT("order",1,"roleId",@v_r_66,"assignable",true),"r68", JSON_OBJECT("order",3,"roleId",@v_r_68,"assignable",true),"r65", JSON_OBJECT("order",0,"roleId",@v_r_65,"assignable",true) )),"r26", JSON_OBJECT("order",7,"roleId",@v_r_26,"assignable",true,"subArrangements",JSON_OBJECT( "r125", JSON_OBJECT("order",1,"roleId",@v_r_125,"assignable",true),"r127", JSON_OBJECT("order",3,"roleId",@v_r_127,"assignable",true),"r124", JSON_OBJECT("order",0,"roleId",@v_r_124,"assignable",true),"r126", JSON_OBJECT("order",2,"roleId",@v_r_126,"assignable",true) )),"r18", JSON_OBJECT("order",8,"roleId",@v_r_18,"assignable",true,"subArrangements",JSON_OBJECT( "r99", JSON_OBJECT("order",3,"roleId",@v_r_99,"assignable",true),"r96", JSON_OBJECT("order",0,"roleId",@v_r_96,"assignable",true),"r98", JSON_OBJECT("order",2,"roleId",@v_r_98,"assignable",true),"r97", JSON_OBJECT("order",1,"roleId",@v_r_97,"assignable",true) )) )),"g3", JSON_OBJECT("name","UI","order",0,"assignable",true,"subArrangements",JSON_OBJECT( "r21", JSON_OBJECT("order",3,"roleId",@v_r_21,"assignable",true,"subArrangements",JSON_OBJECT( "r111", JSON_OBJECT("order",3,"roleId",@v_r_111,"assignable",true),"r109", JSON_OBJECT("order",1,"roleId",@v_r_109,"assignable",true),"r110", JSON_OBJECT("order",2,"roleId",@v_r_110,"assignable",true),"r108", JSON_OBJECT("order",0,"roleId",@v_r_108,"assignable",true) )),"r12", JSON_OBJECT("order",2,"roleId",@v_r_12,"assignable",true,"subArrangements",JSON_OBJECT( "r73", JSON_OBJECT("order",0,"roleId",@v_r_73,"assignable",true),"r75", JSON_OBJECT("order",2,"roleId",@v_r_75,"assignable",true),"r74", JSON_OBJECT("order",1,"roleId",@v_r_74,"assignable",true),"r76", JSON_OBJECT("order",3,"roleId",@v_r_76,"assignable",true) )),"r13", JSON_OBJECT("order",4,"roleId",@v_r_13,"assignable",true,"subArrangements",JSON_OBJECT( "r77", JSON_OBJECT("roleId",@v_r_77,"assignable",true) )),"r8", JSON_OBJECT("order",0,"roleId",@v_r_8,"assignable",true,"subArrangements",JSON_OBJECT( "r61", JSON_OBJECT("order",0,"roleId",@v_r_61,"assignable",true),"r63", JSON_OBJECT("order",2,"roleId",@v_r_63,"assignable",true),"r64", JSON_OBJECT("order",3,"roleId",@v_r_64,"assignable",true),"r62", JSON_OBJECT("order",1,"roleId",@v_r_62,"assignable",true) )),"r11", JSON_OBJECT("order",1,"roleId",@v_r_11,"assignable",true,"subArrangements",JSON_OBJECT( "r70", JSON_OBJECT("order",1,"roleId",@v_r_70,"assignable",true),"r69", JSON_OBJECT("order",0,"roleId",@v_r_69,"assignable",true),"r71", JSON_OBJECT("order",2,"roleId",@v_r_71,"assignable",true),"r72", JSON_OBJECT("order",3,"roleId",@v_r_72,"assignable",true) )) )) )
where id =  @v_profile_appbuilder_owner;

delete from security.security_profile_role where profile_id = @v_profile_appbuilder_owner;

insert into security.security_profile_role(profile_id, role_id) values
(@v_profile_appbuilder_owner, @v_r_1),
(@v_profile_appbuilder_owner, @v_r_2),
(@v_profile_appbuilder_owner, @v_r_3),
(@v_profile_appbuilder_owner, @v_r_4),
(@v_profile_appbuilder_owner, @v_r_5),
(@v_profile_appbuilder_owner, @v_r_6),
(@v_profile_appbuilder_owner, @v_r_7),
(@v_profile_appbuilder_owner, @v_r_8),
(@v_profile_appbuilder_owner, @v_r_9),
(@v_profile_appbuilder_owner, @v_r_10),
(@v_profile_appbuilder_owner, @v_r_11),
(@v_profile_appbuilder_owner, @v_r_12),
(@v_profile_appbuilder_owner, @v_r_13),
(@v_profile_appbuilder_owner, @v_r_14),
(@v_profile_appbuilder_owner, @v_r_15),
(@v_profile_appbuilder_owner, @v_r_16),
(@v_profile_appbuilder_owner, @v_r_17),
(@v_profile_appbuilder_owner, @v_r_18),
(@v_profile_appbuilder_owner, @v_r_19),
(@v_profile_appbuilder_owner, @v_r_20),
(@v_profile_appbuilder_owner, @v_r_21),
(@v_profile_appbuilder_owner, @v_r_22),
(@v_profile_appbuilder_owner, @v_r_23),
(@v_profile_appbuilder_owner, @v_r_24),
(@v_profile_appbuilder_owner, @v_r_25),
(@v_profile_appbuilder_owner, @v_r_26),
(@v_profile_appbuilder_owner, @v_r_32),
(@v_profile_appbuilder_owner, @v_r_33),
(@v_profile_appbuilder_owner, @v_r_34),
(@v_profile_appbuilder_owner, @v_r_35),
(@v_profile_appbuilder_owner, @v_r_37),
(@v_profile_appbuilder_owner, @v_r_38),
(@v_profile_appbuilder_owner, @v_r_39),
(@v_profile_appbuilder_owner, @v_r_40),
(@v_profile_appbuilder_owner, @v_r_43),
(@v_profile_appbuilder_owner, @v_r_44),
(@v_profile_appbuilder_owner, @v_r_45),
(@v_profile_appbuilder_owner, @v_r_46),
(@v_profile_appbuilder_owner, @v_r_47),
(@v_profile_appbuilder_owner, @v_r_48),
(@v_profile_appbuilder_owner, @v_r_49),
(@v_profile_appbuilder_owner, @v_r_50),
(@v_profile_appbuilder_owner, @v_r_53),
(@v_profile_appbuilder_owner, @v_r_54),
(@v_profile_appbuilder_owner, @v_r_55),
(@v_profile_appbuilder_owner, @v_r_56),
(@v_profile_appbuilder_owner, @v_r_57),
(@v_profile_appbuilder_owner, @v_r_58),
(@v_profile_appbuilder_owner, @v_r_59),
(@v_profile_appbuilder_owner, @v_r_60),
(@v_profile_appbuilder_owner, @v_r_61),
(@v_profile_appbuilder_owner, @v_r_62),
(@v_profile_appbuilder_owner, @v_r_63),
(@v_profile_appbuilder_owner, @v_r_64),
(@v_profile_appbuilder_owner, @v_r_65),
(@v_profile_appbuilder_owner, @v_r_66),
(@v_profile_appbuilder_owner, @v_r_67),
(@v_profile_appbuilder_owner, @v_r_68),
(@v_profile_appbuilder_owner, @v_r_69),
(@v_profile_appbuilder_owner, @v_r_70),
(@v_profile_appbuilder_owner, @v_r_71),
(@v_profile_appbuilder_owner, @v_r_72),
(@v_profile_appbuilder_owner, @v_r_73),
(@v_profile_appbuilder_owner, @v_r_74),
(@v_profile_appbuilder_owner, @v_r_75),
(@v_profile_appbuilder_owner, @v_r_76),
(@v_profile_appbuilder_owner, @v_r_77),
(@v_profile_appbuilder_owner, @v_r_78),
(@v_profile_appbuilder_owner, @v_r_79),
(@v_profile_appbuilder_owner, @v_r_80),
(@v_profile_appbuilder_owner, @v_r_81),
(@v_profile_appbuilder_owner, @v_r_82),
(@v_profile_appbuilder_owner, @v_r_83),
(@v_profile_appbuilder_owner, @v_r_84),
(@v_profile_appbuilder_owner, @v_r_85),
(@v_profile_appbuilder_owner, @v_r_86),
(@v_profile_appbuilder_owner, @v_r_87),
(@v_profile_appbuilder_owner, @v_r_88),
(@v_profile_appbuilder_owner, @v_r_89),
(@v_profile_appbuilder_owner, @v_r_90),
(@v_profile_appbuilder_owner, @v_r_91),
(@v_profile_appbuilder_owner, @v_r_92),
(@v_profile_appbuilder_owner, @v_r_93),
(@v_profile_appbuilder_owner, @v_r_94),
(@v_profile_appbuilder_owner, @v_r_95),
(@v_profile_appbuilder_owner, @v_r_96),
(@v_profile_appbuilder_owner, @v_r_97),
(@v_profile_appbuilder_owner, @v_r_98),
(@v_profile_appbuilder_owner, @v_r_99),
(@v_profile_appbuilder_owner, @v_r_100),
(@v_profile_appbuilder_owner, @v_r_101),
(@v_profile_appbuilder_owner, @v_r_102),
(@v_profile_appbuilder_owner, @v_r_103),
(@v_profile_appbuilder_owner, @v_r_104),
(@v_profile_appbuilder_owner, @v_r_105),
(@v_profile_appbuilder_owner, @v_r_106),
(@v_profile_appbuilder_owner, @v_r_107),
(@v_profile_appbuilder_owner, @v_r_108),
(@v_profile_appbuilder_owner, @v_r_109),
(@v_profile_appbuilder_owner, @v_r_110),
(@v_profile_appbuilder_owner, @v_r_111),
(@v_profile_appbuilder_owner, @v_r_112),
(@v_profile_appbuilder_owner, @v_r_113),
(@v_profile_appbuilder_owner, @v_r_114),
(@v_profile_appbuilder_owner, @v_r_115),
(@v_profile_appbuilder_owner, @v_r_116),
(@v_profile_appbuilder_owner, @v_r_117),
(@v_profile_appbuilder_owner, @v_r_118),
(@v_profile_appbuilder_owner, @v_r_119),
(@v_profile_appbuilder_owner, @v_r_120),
(@v_profile_appbuilder_owner, @v_r_121),
(@v_profile_appbuilder_owner, @v_r_122),
(@v_profile_appbuilder_owner, @v_r_123),
(@v_profile_appbuilder_owner, @v_r_124),
(@v_profile_appbuilder_owner, @v_r_125),
(@v_profile_appbuilder_owner, @v_r_126),
(@v_profile_appbuilder_owner, @v_r_127),
(@v_profile_appbuilder_owner, @v_r_159),
(@v_profile_appbuilder_owner, @v_r_160),
(@v_profile_appbuilder_owner, @v_r_161),
(@v_profile_appbuilder_owner, @v_r_162),
(@v_profile_appbuilder_owner, @v_r_163),
(@v_profile_appbuilder_owner, @v_r_164);