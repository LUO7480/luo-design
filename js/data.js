export const categories = [
  { name: '手电', en: 'Flashlights', icon: '✦', description: '小巧可靠的随身照明' },
  { name: '头灯', en: 'Headlamps', icon: '⌁', description: '解放双手的专业光源' },
  { name: '探照灯', en: 'Searchlights', icon: '◉', description: '远距离强光照明' },
  { name: '工作灯', en: 'Work Lights', icon: '▦', description: '面向作业现场的高效照明' },
  { name: '露营灯', en: 'Camping Lights', icon: '△', description: '让户外夜晚更有温度' },
  { name: '应急照明', en: 'Emergency', icon: '⚡', description: '关键时刻保持光明' },
];

export const scenarios = [
  { name: '户外探索', en: 'Outdoor', description: '为徒步、露营和夜间探索提供稳定光源。', icon: '✦', tone: 'red' },
  { name: '工业作业', en: 'Industrial', description: '在检修、施工和维护现场保持清晰视野。', icon: '▦', tone: 'steel' },
  { name: '安防巡检', en: 'Security', description: '为巡逻、值守与夜间作业提供可靠照明。', icon: '◉', tone: 'red' },
  { name: '应急救援', en: 'Emergency', description: '面对突发情况，快速建立可见、可行动的光线。', icon: '⚡', tone: 'steel' },
  { name: '家庭备用', en: 'Home', description: '停电、维修和日常使用都能随手找到光。', icon: '⌁', tone: 'red' },
  { name: '汽车维修', en: 'Automotive', description: '照亮发动机舱、底盘和车内每一个角落。', icon: '◇', tone: 'steel' },
];

export const siteStats = [
  { value: '15+', label: '年移动照明经验' },
  { value: '6', label: '大产品系列' },
  { value: '200+', label: '专业团队成员' },
  { value: '100+', label: '服务国家与地区' },
  { value: '24/7', label: '合作响应支持' },
];

export const products = [
  {
    id: 'wrs-pocket-01', name: 'WRS P01 便携手电', category: '手电', scenario: ['户外探索', '家庭备用'],
    tagline: '随身携带的可靠光源', description: '轻量机身与高效光学系统，适合日常通勤、户外探索和家庭备用。',
    features: ['便携机身', '磁吸尾盖', 'Type-C 充电'], specs: [['产品定位', '便携手电'], ['使用场景', '户外 / 家庭'], ['充电方式', 'Type-C']], image: 'assets/placeholders/pocket.svg', featured: true,
  },
  {
    id: 'wrs-headlamp-01', name: 'WRS H01 专业头灯', category: '头灯', scenario: ['户外探索', '工业作业'],
    tagline: '把光线留给正在做的事', description: '稳定佩戴与灵活照明结合，为夜间作业和户外活动释放双手。',
    features: ['可调节头带', '多档照明', '防泼溅机身'], specs: [['产品定位', '专业头灯'], ['使用场景', '户外 / 工业'], ['佩戴方式', '头戴']], image: 'assets/placeholders/headlamp.svg', featured: true,
  },
  {
    id: 'wrs-search-01', name: 'WRS S01 远射探照灯', category: '探照灯', scenario: ['安防巡检', '应急救援'],
    tagline: '让远处也清晰可见', description: '聚光远射与大容量电源组合，适合巡检、救援与夜间户外使用。',
    features: ['远距离聚光', '大容量电源', '肩带便携'], specs: [['产品定位', '远射探照灯'], ['使用场景', '巡检 / 救援'], ['携带方式', '手持 / 肩带']], image: 'assets/placeholders/searchlight.svg', featured: true,
  },
  {
    id: 'wrs-work-01', name: 'WRS W01 折叠工作灯', category: '工作灯', scenario: ['工业作业', '汽车维修'],
    tagline: '为现场工作提供清晰视野', description: '可折叠结构与多角度支撑，让维修和施工现场获得更灵活的照明。',
    features: ['折叠支架', '多角度照明', '背部磁吸'], specs: [['产品定位', '多功能工作灯'], ['使用场景', '工业 / 汽车'], ['安装方式', '磁吸 / 支架']], image: 'assets/placeholders/worklight.svg', featured: false,
  },
  {
    id: 'wrs-camp-01', name: 'WRS C01 营地氛围灯', category: '露营灯', scenario: ['户外探索', '家庭备用'],
    tagline: '把营地变成温暖的家', description: '柔和泛光与便携设计，适合营地照明、夜间聚会与室内备用。',
    features: ['柔和泛光', '悬挂提手', '长效续航'], specs: [['产品定位', '营地灯'], ['使用场景', '露营 / 家庭'], ['使用方式', '手提 / 悬挂']], image: 'assets/placeholders/camping.svg', featured: false,
  },
  {
    id: 'wrs-emergency-01', name: 'WRS E01 应急照明灯', category: '应急照明', scenario: ['应急救援', '家庭备用'],
    tagline: '关键时刻，光一直在', description: '兼顾日常备用与突发停电场景，帮助家庭和现场快速恢复照明。',
    features: ['停电应急', '多种摆放方式', '状态指示'], specs: [['产品定位', '应急照明'], ['使用场景', '家庭 / 救援'], ['摆放方式', '桌面 / 悬挂']], image: 'assets/placeholders/emergency.svg', featured: false,
  },
  {
    id: 'wrs-inspect-01', name: 'WRS I01 检修笔灯', category: '手电', scenario: ['工业作业', '汽车维修'],
    tagline: '狭窄空间里的精准补光', description: '细长机身方便随身携带，适用于设备检修和细节观察。',
    features: ['细长机身', '夹扣携带', '近距离补光'], specs: [['产品定位', '检修笔灯'], ['使用场景', '检修 / 维修'], ['携带方式', '夹扣 / 口袋']], image: 'assets/placeholders/inspect.svg', featured: false,
  },
  {
    id: 'wrs-area-01', name: 'WRS A01 区域泛光灯', category: '工作灯', scenario: ['工业作业', '应急救援'],
    tagline: '照亮更大的工作区域', description: '宽角度泛光与稳定支撑适合工作面、仓储及应急现场照明。',
    features: ['宽角度泛光', '稳定支撑', '现场易部署'], specs: [['产品定位', '区域泛光灯'], ['使用场景', '工业 / 救援'], ['部署方式', '支架 / 地面']], image: 'assets/placeholders/area.svg', featured: false,
  },
];

export function filterProducts(items, { category = '', scenario = '' } = {}) {
  return items.filter((product) => {
    const categoryMatch = !category || product.category === category;
    const scenarioMatch = !scenario || product.scenario.includes(scenario);
    return categoryMatch && scenarioMatch;
  });
}

export function getProductById(items, id) {
  return items.find((product) => product.id === id) || null;
}

export function wrapIndex(index, length) {
  return ((index % length) + length) % length;
}
