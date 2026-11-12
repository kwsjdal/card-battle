-- =====================================================================
-- 干员档案介绍补全（2026-10-23）
-- cards.description 此前大多为空或英文占位符，这里补成明日方舟风格档案。
-- 格式固定含「XX干员」，前端干员详情页据此解析职业标签。
-- 幂等：可重复执行。
-- =====================================================================
USE card_battle;

UPDATE cards SET description='罗德岛行动组A4队长，近卫干员。性格冷静认真，白刃战能力在同期干员中首屈一指。' WHERE id=1;
UPDATE cards SET description='罗德岛行动组A4成员，狙击干员。性格活泼爱开玩笑，射术稳定，能持续压制敌人掩护队友。' WHERE id=2;
UPDATE cards SET description='罗德岛行动组A4成员，重装干员。出身贫寒却乐观坚韧，用大型盾牌为小队挡下所有火力。' WHERE id=3;
UPDATE cards SET description='罗德岛行动组A4成员，术师干员。法术天赋出众但情绪易失控，中二气十足，痴迷研究源石技艺。' WHERE id=4;
UPDATE cards SET description='前地下拳击手，近卫干员。性格直率热情，拳法迅猛，相信自己的双拳胜过任何武器。' WHERE id=5;
UPDATE cards SET description='叙拉古沃尔西尼家族大小姐，近卫干员。随性不羁的狼之少女，源石剑术凌厉而难以捉摸。' WHERE id=6;
UPDATE cards SET description='罗德岛行动预备组A1队长，先锋干员。沉默寡言，执行力极强，永远第一个冲进战场。' WHERE id=7;
UPDATE cards SET description='莱茵生命出身的医疗干员。言语冷淡但医术高明，负责罗德岛战地急救与后方支援。' WHERE id=8;
UPDATE cards SET description='前龙门近卫局特别监察组组长，重装干员。鬼族武士，坚盾与忠义并重，人称「鬼姐」。' WHERE id=9;
UPDATE cards SET description='深海猎人成员，近卫干员。独来独往的 killer whale，持巨剑独行于大地，狩猎海嗣。' WHERE id=10;
UPDATE cards SET description='行踪不明的炎国工匠，重装干员。言谈古风，所铸重器可当千军，喜怒皆成谜。' WHERE id=11;
UPDATE cards SET description='卡兹戴尔骑士世家出身，重装干员。耀骑士玛恩纳之妹，坚盾之下亦有仁心。' WHERE id=12;
UPDATE cards SET description='罗德岛行动预备组A4成员，近卫干员。寡言的青年，挥舞巨型镰刀，沉默却可靠。' WHERE id=13;
UPDATE cards SET description='活泼的浮空信使少女，辅助干员。操纵反重力源石技艺，喜欢恶作剧却也认真可靠。' WHERE id=14;
UPDATE cards SET description='炎国司职吏员，术师干员。雷法精湛，一丝不苟，对纪律与规矩有着近乎执念的坚持。' WHERE id=15;
UPDATE cards SET description='莱茵生命研究员，医疗干员。冷静理性的医师，心中始终装着医学最初的理想。' WHERE id=16;
UPDATE cards SET description='谢拉格雪境之主，近卫干员。喀兰贸易董事长，沉稳睿智，为家族与雪山谋划一切。' WHERE id=17;
UPDATE cards SET description='火山学者，术师干员。听力不佳却感知敏锐，能操纵炽烈的火山源石技艺焚尽来敌。' WHERE id=18;
UPDATE cards SET description='从乌萨斯走出的流浪战士，重装干员。沉默坚毅，岩石般的意志护佑每一位同行者。' WHERE id=19;
UPDATE cards SET description='企鹅物流王牌，狙击干员。性格高调爱炫，双枪火力全开，有她在派对永远不会冷场。' WHERE id=20;
UPDATE cards SET description='来自荒野的兽群饲养员，先锋干员。性格温和腼腆，带着可爱的小兽伙伴一同作战。' WHERE id=21;
UPDATE cards SET description='前警戒警卫队队员，先锋干员。纪律严明，枪术利落，退役后加入罗德岛继续服役。' WHERE id=22;
UPDATE cards SET description='罗德岛行动预备组A4成员，近卫干员。自称「社交界的明星」，风流言谈下藏着可靠本色。' WHERE id=23;
UPDATE cards SET description='罗德岛行动预备组A1成员，先锋干员。认真稳重的模范生，擅长指挥队友与快速突击。' WHERE id=24;
UPDATE cards SET description='来历不明的神秘干员，术师干员。总是戴着防毒面具，操使着古老而危险的源石技艺。' WHERE id=25;
UPDATE cards SET description='元气满满的先锋干员。活泼好动，喜欢黏着前辈撒娇，战场上却意外地靠谱。' WHERE id=26;
UPDATE cards SET description='沉默的战场清道夫，先锋干员。行动迅速隐秘，擅长渗透清理，从不谈论自己的过去。' WHERE id=27;
UPDATE cards SET description='出身东国的剑士少女，近卫干员。温柔有礼，挥舞长刀时却锐不可当。' WHERE id=28;
UPDATE cards SET description='平时温和、失控时危险，近卫干员。巨刃与体术并行，一直在学习控制自己的力量。' WHERE id=29;
UPDATE cards SET description='卡西米尔无胄盟刺客，狙击干员。冷淡寡言，百步之外一击必中，「白金」之名令人胆寒。' WHERE id=30;
UPDATE cards SET description='爱好制作甜点的毒物专家，狙击干员。笑容甜美箭矢淬毒，狡黠又机智。' WHERE id=31;
UPDATE cards SET description='前佣兵狙击手，狙击干员。沉稳可靠的重炮手，她的一炮足以粉碎整面城墙。' WHERE id=32;
UPDATE cards SET description='出身荒野的猎手，狙击干员。孤身行走林间，枪声响起之处猎物应声而倒。' WHERE id=33;
UPDATE cards SET description='帕尔维斯调香师世家出身，医疗干员。以香气抚慰伤病，温柔优雅的战地医师。' WHERE id=34;
UPDATE cards SET description='来自炎国的药剂师，医疗干员。性格温和腼腆，默默守护队友，医者仁心。' WHERE id=35;
UPDATE cards SET description='阿卡胡拉出身的医生，医疗干员。医术野蛮但有效——先把人打晕再治，也算治好了。' WHERE id=36;
UPDATE cards SET description='天真的重装干员。抱着心爱的滑板冲锋陷阵，快乐至上，盾后是全队的笑声。' WHERE id=37;
UPDATE cards SET description='乌萨斯学生自治团成员，重装干员。背着「做饭用」的大盾，是队伍温暖的后勤担当。' WHERE id=38;
UPDATE cards SET description='企鹅物流成员，先锋干员。沉默寡言的狼族女性，剑术利落，任何委托永远准时送达。' WHERE id=39;
UPDATE cards SET description='前盾卫队成员，重装干员。豪爽的大姐头，巨盾横扫，谁也别想越过她的防线。' WHERE id=40;
UPDATE cards SET description='天灾信使，狙击干员。与伙伴狼「霜色」同行，风雨兼程，箭无虚发。' WHERE id=41;
UPDATE cards SET description='活了数百年的血魔医师，医疗干员。罗德岛元老，毒舌刻薄，医术却冠绝当世。' WHERE id=42;
UPDATE cards SET description='前秘装佣兵，重装干员。油滑爱财却极重情义，防爆盾一立，滴水不漏。' WHERE id=43;
UPDATE cards SET description='黑钢国际护卫组长，重装干员。严谨认真的雷神之子，盾与电光构筑起钢铁防线。' WHERE id=44;
UPDATE cards SET description='谢拉格圣山之女，辅助干员。尊贵的圣女，歌声可净化人心，亦蕴含雪山的伟力。' WHERE id=45;
UPDATE cards SET description='莱茵生命防御工程师，辅助干员。机械装置「咪波」的创造者，天才发明家兼吸猫狂人。' WHERE id=46;
UPDATE cards SET description='代号「斯芬克斯」，特种干员。沉默的狼之杀手，只遵从「外婆」的命令展开猎杀。' WHERE id=47;
UPDATE cards SET description='炎国武馆传人，特种干员。如今的影业明星，拳法刚猛，依旧行侠仗义。' WHERE id=48;
UPDATE cards SET description='龙门近卫局局长，近卫干员。「陈晖洁」，佩剑赤霄，雷厉风行的龙之女。' WHERE id=49;
UPDATE cards SET description='格拉斯哥帮之王，先锋干员。流亡的阿斯兰王族，重锤所向，为王先驱。' WHERE id=50;
UPDATE cards SET description='希尔战争孤儿，医疗干员。沉默的圣女「夜莺」，以吟唱疗愈之歌守护众人。' WHERE id=51;
UPDATE cards SET description='前萨卡兹赦罪师，医疗干员。背负巨剑的修女，医术与守护之光普照全队。' WHERE id=52;
UPDATE cards SET description='莱茵生命前防御科主任，重装干员。科学家与坚盾的结合体，理性与守护并存。' WHERE id=53;
UPDATE cards SET description='失忆的流浪者，近卫干员。操纵毁灭性的烈焰巨剑，燃烧一切胆敢阻挡之人。' WHERE id=54;
UPDATE cards SET description='维多利亚皇家近卫学校毕业生，先锋干员。性格爽朗的号手，冲锋号角永不停歇。' WHERE id=55;
UPDATE cards SET description='极北科考探险家，辅助干员。驾驶自造无人机队，永远向着未知疆域前进。' WHERE id=56;
UPDATE cards SET description='罗德岛的领袖，术师干员。少女之王阿米娅，为了受苦之人奔走于大地之间。' WHERE id=57;
UPDATE cards SET description='莱茵生命实验体，术师干员。体内蕴藏毁灭性炎能，任性妄为却渴望被爱。' WHERE id=58;
UPDATE cards SET description='乌萨斯帝国前将军，近卫干员。「指头」赫拉格，为保卫家族与尊严执剑而战。' WHERE id=59;
UPDATE cards SET description='萨卡兹佣兵，狙击干员。喜欢炸弹与戏谑的复仇者，笑里藏刀，无人猜得透她。' WHERE id=60;
UPDATE cards SET description='罗德岛医疗部门主管，医疗干员。博识而冷峻的 Mon3tr 主人，掌握罗德岛最深的秘密。' WHERE id=61;
UPDATE cards SET description='东国玉盛苏家千金，辅助干员。九尾狐少女铃兰，温柔的祈祷中蕴含缚敌之力。' WHERE id=62;
UPDATE cards SET description='罗德岛精锐近卫，近卫干员。链条巨刃火力持久，大姐姐气场十足，战斗永不停歇。' WHERE id=63;
UPDATE cards SET description='阿卡胡拉巨械工程师，重装干员。操纵自制巨型机甲，力大无穷，谁也拦不住。' WHERE id=64;
UPDATE cards SET description='莱茵生命科研成果，特种干员。机械体「温蒂」，以强劲气流把敌人推离阵地。' WHERE id=65;
UPDATE cards SET description='炎国画师，术师干员。「夕」，笔下丹青自成天地，一念山河，一念春秋。' WHERE id=66;
