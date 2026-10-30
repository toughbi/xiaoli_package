"use strict";

const STORAGE_KEY = "daytrace-ledger-records-v1";
const DISMISSED_AUTO_KEY = "daytrace-ledger-dismissed-auto-v1";
const CURRENCY = new Intl.NumberFormat("zh-CN", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const CHANNELS = [
  { id: "wechat", name: "微信支付", short: "微", color: "#555851", bg: "#f0f0ec" },
  { id: "alipay", name: "支付宝", short: "支", color: "#555851", bg: "#f0f0ec" },
  { id: "bank", name: "银行卡", short: "卡", color: "#555851", bg: "#f0f0ec" },
  { id: "unionpay", name: "云闪付", short: "云", color: "#555851", bg: "#f0f0ec" },
  { id: "applepay", name: "Apple Pay", short: "A", color: "#555851", bg: "#f0f0ec" },
  { id: "paypal", name: "PayPal", short: "P", color: "#555851", bg: "#f0f0ec" },
  { id: "other", name: "其他渠道", short: "其", color: "#555851", bg: "#f0f0ec" },
];

const CATEGORY_GROUPS = {
  expense: [
    { name: "餐饮", icon: "筷" },
    { name: "交通", icon: "行" },
    { name: "购物", icon: "购" },
    { name: "居住", icon: "家" },
    { name: "娱乐", icon: "乐" },
    { name: "医疗", icon: "医" },
    { name: "学习", icon: "学" },
    { name: "人情", icon: "礼" },
    { name: "通讯", icon: "讯" },
    { name: "其他", icon: "···" },
  ],
  income: [
    { name: "工资", icon: "薪" },
    { name: "奖金", icon: "奖" },
    { name: "理财", icon: "利" },
    { name: "收款", icon: "收" },
    { name: "退款", icon: "退" },
    { name: "红包", icon: "包" },
    { name: "兼职", icon: "兼" },
    { name: "补贴", icon: "补" },
    { name: "报销", icon: "报" },
    { name: "其他", icon: "···" },
  ],
};

const DONUT_COLORS = ["#2b2d29", "#676a64", "#8d9089", "#b3b5ae", "#cecec8", "#deded9", "#b08d57"];

const DEEPSEEK_MODELS = {
  "deepseek-chat": "deepseek-chat · 推荐，适合收支提取",
  "deepseek-reasoner": "deepseek-reasoner · 深度推理，速度较慢",
};
const DEEPSEEK_ENDPOINT = "https://api.deepseek.com/v1/chat/completions";

const dom = {
  todayBalance: document.querySelector("#todayBalance"),
  todayIncome: document.querySelector("#todayIncome"),
  todayExpense: document.querySelector("#todayExpense"),
  todayCount: document.querySelector("#todayCount"),
  todayDateBadge: document.querySelector("#todayDateBadge"),
  todayRecordCount: document.querySelector("#todayRecordCount"),
  todayRecordsList: document.querySelector("#todayRecordsList"),
  analysisExpense: document.querySelector("#analysisExpense"),
  analysisIncome: document.querySelector("#analysisIncome"),
  analysisBalance: document.querySelector("#analysisBalance"),
  analysisPeriodLabel: document.querySelector("#analysisPeriodLabel"),
  analysisChart: document.querySelector("#analysisChart"),
  channelPeriodLabel: document.querySelector("#channelPeriodLabel"),
  averageDailyExpense: document.querySelector("#averageDailyExpense"),
  activeDays: document.querySelector("#activeDays"),
  monthLabel: document.querySelector("#monthLabel"),
  heroPeriod: document.querySelector("#heroPeriod"),
  monthButton: document.querySelector("#monthButton"),
  trendChart: document.querySelector("#trendChart"),
  weeklyExpense: document.querySelector("#weeklyExpense"),
  todayLabel: document.querySelector("#todayLabel"),
  topbarDate: document.querySelector("#topbarDate"),
  recordCount: document.querySelector("#recordCount"),
  recordsList: document.querySelector("#recordsList"),
  searchInput: document.querySelector("#searchInput"),
  channelFilter: document.querySelector("#channelFilter"),
  channelDonut: document.querySelector("#channelDonut"),
  channelLegend: document.querySelector("#channelLegend"),
  donutTotal: document.querySelector("#donutTotal"),
  insightText: document.querySelector("#insightText"),
  insightAction: document.querySelector("#insightAction"),
  recordDialog: document.querySelector("#recordDialog"),
  recordForm: document.querySelector("#recordForm"),
  dialogTitle: document.querySelector("#dialogTitle"),
  recordId: document.querySelector("#recordId"),
  recordType: document.querySelector("#recordType"),
  amountInput: document.querySelector("#amountInput"),
  amountError: document.querySelector("#amountError"),
  dateInput: document.querySelector("#dateInput"),
  channelInput: document.querySelector("#channelInput"),
  categoryInput: document.querySelector("#categoryInput"),
  categoryOptions: document.querySelector("#categoryOptions"),
  noteInput: document.querySelector("#noteInput"),
  deleteRecordButton: document.querySelector("#deleteRecordButton"),
  monthDialog: document.querySelector("#monthDialog"),
  yearSelect: document.querySelector("#yearSelect"),
  monthSelect: document.querySelector("#monthSelect"),
  manageDialog: document.querySelector("#manageDialog"),
  totalRecordCount: document.querySelector("#totalRecordCount"),
  confirmDialog: document.querySelector("#confirmDialog"),
  confirmTitle: document.querySelector("#confirmTitle"),
  confirmMessage: document.querySelector("#confirmMessage"),
  confirmActionButton: document.querySelector("#confirmActionButton"),
  importInput: document.querySelector("#importInput"),
  toast: document.querySelector("#toast"),
  nativePermissionPanel: document.querySelector("#nativePermissionPanel"),
  permissionPill: document.querySelector("#permissionPill"),
  notificationPermissionButton: document.querySelector("#notificationPermissionButton"),
  smsPermissionButton: document.querySelector("#smsPermissionButton"),
  manageStorageNote: document.querySelector("#manageStorageNote"),
  notificationDiagnosticButton: document.querySelector("#notificationDiagnosticButton"),
  diagnosticDialog: document.querySelector("#diagnosticDialog"),
  diagnosticSummary: document.querySelector("#diagnosticSummary"),
  diagnosticList: document.querySelector("#diagnosticList"),
  aiSettingsButton: document.querySelector("#aiSettingsButton"),
  aiPanelStatus: document.querySelector("#aiPanelStatus"),
  aiSettingsDialog: document.querySelector("#aiSettingsDialog"),
  aiSettingsForm: document.querySelector("#aiSettingsForm"),
  aiMode: document.querySelector("#aiMode"),
  aiEndpoint: document.querySelector("#aiEndpoint"),
  aiModel: document.querySelector("#aiModel"),
  aiApiKey: document.querySelector("#aiApiKey"),
  aiEnabled: document.querySelector("#aiEnabled"),
  aiConsent: document.querySelector("#aiConsent"),
  aiSettingsStatus: document.querySelector("#aiSettingsStatus"),
  testAiConnectionButton: document.querySelector("#testAiConnectionButton"),
  refreshDeepSeekModelsButton: document.querySelector("#refreshDeepSeekModelsButton"),
  cloudVoicePanelStatus: document.querySelector("#cloudVoicePanelStatus"),
  cloudSpeechSettingsButton: document.querySelector("#cloudSpeechSettingsButton"),
  cloudSpeechSettingsDialog: document.querySelector("#cloudSpeechSettingsDialog"),
  cloudSpeechSettingsForm: document.querySelector("#cloudSpeechSettingsForm"),
  cloudSpeechEngine: document.querySelector("#cloudSpeechEngine"),
  cloudSpeechSecretId: document.querySelector("#cloudSpeechSecretId"),
  cloudSpeechSecretKey: document.querySelector("#cloudSpeechSecretKey"),
  cloudSpeechEnabled: document.querySelector("#cloudSpeechEnabled"),
  cloudSpeechConsent: document.querySelector("#cloudSpeechConsent"),
  cloudSpeechSettingsStatus: document.querySelector("#cloudSpeechSettingsStatus"),
  navVoiceButton: document.querySelector("#navVoiceButton"),
  todayManualAddButton: document.querySelector("#todayManualAddButton"),
  voiceDialog: document.querySelector("#voiceDialog"),
  voiceTranscript: document.querySelector("#voiceTranscript"),
  voiceResults: document.querySelector("#voiceResults"),
  voiceNote: document.querySelector("#voiceNote"),
  voiceConfirmButton: document.querySelector("#voiceConfirmButton"),
  voiceRetryButton: document.querySelector("#voiceRetryButton"),
  voiceManualButton: document.querySelector("#voiceManualButton"),
  voiceHoldHud: document.querySelector("#voiceHoldHud"),
  voiceHoldTitle: document.querySelector("#voiceHoldTitle"),
  voiceHoldTranscript: document.querySelector("#voiceHoldTranscript"),
};

let records = loadRecords();
let selectedMonth = toMonthKey(new Date());
let analyticsMode = "week";
let showAllRecords = false;
let pendingConfirm = null;
let toastTimer = null;
let voiceDraft = [];
let voiceTranscriptText = "";
let webSpeechRecognition = null;
let webSpeechFinalText = "";
let voiceSessionActive = false;
let voiceHolding = false;
let voiceStopTimer = null;
let voicePressPointerId = null;
const appIconCache = new Map();

function uid() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function loadRecords() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
    if (!Array.isArray(stored)) return [];
    return stored.filter(isValidRecord).map(normalizeRecord);
  } catch (error) {
    console.warn("无法读取本地记录", error);
    return [];
  }
}

function isValidRecord(record) {
  return (
    record &&
    typeof record === "object" &&
    ["income", "expense"].includes(record.type) &&
    Number.isFinite(Number(record.amount)) &&
    Number(record.amount) > 0 &&
    /^\d{4}-\d{2}-\d{2}$/.test(record.date)
  );
}

function normalizeRecord(record) {
  const channel = CHANNELS.some((item) => item.id === record.channel) ? record.channel : "other";
  const categories = CATEGORY_GROUPS[record.type].map((item) => item.name);
  return {
    id: String(record.id || uid()),
    type: record.type,
    amount: roundMoney(Number(record.amount)),
    date: record.date,
    channel,
    category: categories.includes(record.category) ? record.category : "其他",
    note: String(record.note || "").slice(0, 40),
    createdAt: record.createdAt || new Date().toISOString(),
    autoCaptured: Boolean(record.autoCaptured),
    voiceCaptured: Boolean(record.voiceCaptured),
    sourceLabel: String(record.sourceLabel || "").slice(0, 30),
    sourcePackage: String(
      record.sourcePackage ||
      (record.sourceKey && !String(record.sourceKey).startsWith("sms:") ? record.sourceKey : "") ||
      "",
    ).slice(0, 160),
    confidence: ["high", "medium", "low"].includes(record.confidence) ? record.confidence : undefined,
    explanation: String(record.explanation || "").slice(0, 240),
  };
}

function saveRecords() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  } catch (error) {
    console.error("无法保存记录", error);
    showToast("浏览器存储空间不足，请先导出备份");
  }
}

function roundMoney(value) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function formatMoney(value, withSymbol = true) {
  const number = Number(value) || 0;
  const amount = CURRENCY.format(Math.abs(number));
  const sign = number < 0 ? "−" : "";
  return withSymbol ? `${sign}¥${amount}` : `${sign}${amount}`;
}

function formatCompactMoney(value) {
  const amount = Number(value) || 0;
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(1)}万`;
  if (amount >= 1000) return `¥${(amount / 1000).toFixed(1)}k`;
  return `¥${Math.round(amount)}`;
}

function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function toMonthKey(date) {
  return localDateKey(date).slice(0, 7);
}

function dateFromKey(dateKey) {
  const [year, month, day] = dateKey.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatMonthLabel(monthKey) {
  const [year, month] = monthKey.split("-").map(Number);
  const now = new Date();
  if (year === now.getFullYear() && month === now.getMonth() + 1) return "本月";
  if (year === now.getFullYear()) return `${month} 月`;
  return `${year} 年 ${month} 月`;
}

function formatDateHeading(dateKey) {
  const date = dateFromKey(dateKey);
  const today = localDateKey(new Date());
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  if (dateKey === today) return "今天";
  if (dateKey === localDateKey(yesterday)) return "昨天";
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`;
}

function weekdayName(dateKey) {
  return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][
    dateFromKey(dateKey).getDay()
  ];
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function getChannel(channelId) {
  return CHANNELS.find((channel) => channel.id === channelId) || CHANNELS.at(-1);
}

function getCategoryIcon(category, type) {
  return CATEGORY_GROUPS[type].find((item) => item.name === category)?.icon || "记";
}

function getMonthRecords() {
  return records.filter((record) => record.date.startsWith(selectedMonth));
}

function summarize(list) {
  return list.reduce(
    (result, record) => {
      result[record.type] = roundMoney(result[record.type] + record.amount);
      return result;
    },
    { income: 0, expense: 0 },
  );
}

function renderAll() {
  renderDashboard();
  renderTrend();
  renderAnalytics();
  renderRecords();
  renderManageInfo();
}

function renderDashboard() {
  const todayKey = localDateKey(new Date());
  const todayRecords = records.filter((record) => record.date === todayKey);
  const summary = summarize(todayRecords);
  const balance = roundMoney(summary.income - summary.expense);
  const date = dateFromKey(todayKey);
  dom.todayBalance.textContent = formatMoney(balance, false);
  dom.todayIncome.textContent = formatMoney(summary.income);
  dom.todayExpense.textContent = formatMoney(summary.expense);
  dom.todayCount.textContent = `${todayRecords.length} 笔`;
  dom.todayDateBadge.textContent =
    `${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${weekdayName(todayKey)}`;
  renderDashboardRecords(todayRecords);
}

function renderDashboardRecords(list) {
  dom.todayRecordCount.textContent = `${list.length} 笔`;
  if (!list.length) {
    dom.todayRecordsList.innerHTML = `
      <div class="today-empty">
        <p>今天还没有收支记录。</p>
        <button class="text-button today-add" type="button">记下第一笔</button>
      </div>
    `;
    dom.todayRecordsList.querySelector(".today-add").addEventListener("click", () => openRecordDialog());
    return;
  }
  dom.todayRecordsList.innerHTML = `
    <div class="today-record-list">
      ${[...list]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((record) => recordRowMarkup(record))
        .join("")}
    </div>
  `;
  bindRecordButtons(dom.todayRecordsList);
}

function startOfWeek(date) {
  const value = new Date(date);
  value.setHours(0, 0, 0, 0);
  const offset = (value.getDay() + 6) % 7;
  value.setDate(value.getDate() - offset);
  return value;
}

function getAnalyticsPeriod() {
  const now = new Date();
  if (analyticsMode === "week") {
    const start = startOfWeek(now);
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const startKey = localDateKey(start);
    const endKey = localDateKey(end);
    const periodRecords = records.filter((record) => record.date >= startKey && record.date <= endKey);
    const groups = Array.from({ length: 7 }, (_, index) => {
      const date = new Date(start);
      date.setDate(date.getDate() + index);
      const dateKey = localDateKey(date);
      return {
        label: weekdayName(dateKey).replace("周", ""),
        dateKey,
        records: periodRecords.filter((record) => record.date === dateKey),
      };
    });
    return {
      records: periodRecords,
      label: `${start.getMonth() + 1} 月 ${start.getDate()} 日 – ${end.getMonth() + 1} 月 ${end.getDate()} 日`,
      shortLabel: "本周",
      elapsedDays: Math.min(7, Math.max(1, Math.ceil((now - start) / 86_400_000) + 1)),
      groups,
      chartTitle: "周度趋势",
    };
  }

  const [year, month] = selectedMonth.split("-").map(Number);
  const daysInMonth = new Date(year, month, 0).getDate();
  const monthRecords = getMonthRecords();
  const groups = [];
  for (let day = 1; day <= daysInMonth; day += 7) {
    const end = Math.min(day + 6, daysInMonth);
    groups.push({
      label: `${day}–${end}`,
      records: monthRecords.filter((record) => {
        const recordDay = Number(record.date.slice(8, 10));
        return recordDay >= day && recordDay <= end;
      }),
    });
  }
  const currentMonth = toMonthKey(now) === selectedMonth;
  return {
    records: monthRecords,
    label: `${year} 年 ${month} 月`,
    shortLabel: "本月",
    elapsedDays: currentMonth ? now.getDate() : daysInMonth,
    groups,
    chartTitle: "月度趋势",
  };
}

function renderAnalysisChart(groups) {
  const data = groups.map((group) => ({
    ...group,
    ...summarize(group.records),
  }));
  const max = Math.max(1, ...data.flatMap((item) => [item.income, item.expense]));
  const maxBarHeight = window.innerWidth <= 680 ? 96 : 128;
  dom.analysisChart.style.gridTemplateColumns = `repeat(${Math.max(1, data.length)}, 1fr)`;
  dom.analysisChart.innerHTML = data
    .map((item) => {
      const incomeHeight = item.income ? Math.max(3, (item.income / max) * maxBarHeight) : 0;
      const expenseHeight = item.expense ? Math.max(3, (item.expense / max) * maxBarHeight) : 0;
      return `
        <div class="chart-day" title="${escapeHtml(item.label)} 收入 ${formatMoney(item.income)}，支出 ${formatMoney(item.expense)}">
          <div class="chart-bars">
            <i class="chart-bar income-bar ${item.income ? "" : "empty"}" style="height:${incomeHeight}px"></i>
            <i class="chart-bar expense-bar ${item.expense ? "" : "empty"}" style="height:${expenseHeight}px"></i>
          </div>
          <span>${escapeHtml(item.label)}</span>
        </div>
      `;
    })
    .join("");
}

function renderAnalytics() {
  const period = getAnalyticsPeriod();
  const summary = summarize(period.records);
  const balance = roundMoney(summary.income - summary.expense);
  dom.analysisPeriodLabel.textContent = period.label;
  dom.analysisExpense.textContent = formatMoney(summary.expense);
  dom.analysisIncome.textContent = formatMoney(summary.income);
  dom.analysisBalance.textContent = formatMoney(balance);
  dom.averageDailyExpense.textContent =
    formatMoney(summary.expense / Math.max(1, period.elapsedDays));
  dom.activeDays.textContent =
    `${new Set(period.records.map((record) => record.date)).size} 天`;
  document.querySelector("#analysisTrendTitle").textContent = period.chartTitle;
  document.querySelectorAll("[data-period]").forEach((button) => {
    button.classList.toggle("active", button.dataset.period === analyticsMode);
  });
  dom.monthButton.hidden = analyticsMode !== "month";
  updateMonthLabel();
  renderAnalysisChart(period.groups);
  renderChannels(period.records, period.shortLabel);
  renderInsight(period.records, period.shortLabel);
}

function renderTrend() {
  const dates = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(12, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    return localDateKey(date);
  });

  const dayData = dates.map((dateKey) => {
    const daily = summarize(records.filter((record) => record.date === dateKey));
    return { dateKey, ...daily, total: daily.income + daily.expense };
  });
  const max = Math.max(1, ...dayData.flatMap((day) => [day.income, day.expense]));
  const today = localDateKey(new Date());
  const maxBarHeight = window.innerWidth <= 680 ? 112 : 138;

  dom.trendChart.innerHTML = dayData
    .map((day) => {
      const incomeHeight = day.income ? Math.max(4, (day.income / max) * maxBarHeight) : 0;
      const expenseHeight = day.expense ? Math.max(4, (day.expense / max) * maxBarHeight) : 0;
      const weekday = weekdayName(day.dateKey);
      const dayNumber = dateFromKey(day.dateKey).getDate();
      return `
        <div class="chart-day ${day.dateKey === today ? "today" : ""}" title="${escapeHtml(weekday)} 收入 ${formatMoney(day.income)}，支出 ${formatMoney(day.expense)}">
          <div class="chart-bars">
            <i class="chart-bar income-bar ${day.income ? "" : "empty"}" style="height:${incomeHeight}px"></i>
            <i class="chart-bar expense-bar ${day.expense ? "" : "empty"}" style="height:${expenseHeight}px"></i>
          </div>
          <span>${weekday.slice(1)} ${dayNumber}</span>
        </div>
      `;
    })
    .join("");

  const totals = {
    income: dayData.reduce((sum, day) => sum + day.income, 0),
    expense: dayData.reduce((sum, day) => sum + day.expense, 0),
  };
  dom.weeklyExpense.textContent = `7 日支出 ${formatMoney(totals.expense)}`;
  dom.trendChart.setAttribute(
    "aria-label",
    `近七日收入 ${formatMoney(totals.income)}，支出 ${formatMoney(totals.expense)}`,
  );
}

function getDisplayRecords() {
  const query = dom.searchInput.value.trim().toLowerCase();
  const channel = dom.channelFilter.value;
  let filtered = showAllRecords ? [...records] : getMonthRecords();
  if (channel !== "all") filtered = filtered.filter((record) => record.channel === channel);
  if (query) {
    filtered = filtered.filter((record) => {
      const channelName = getChannel(record.channel).name;
      return [record.note, record.category, channelName, record.type === "income" ? "收入" : "支出"]
        .join(" ")
        .toLowerCase()
        .includes(query);
    });
  }
  return filtered.sort((a, b) => {
    const dateDiff = b.date.localeCompare(a.date);
    return dateDiff || b.createdAt.localeCompare(a.createdAt);
  });
}

function recordRowMarkup(record, options = {}) {
  const channel = getChannel(record.channel);
  const sourceBadge = record.voiceCaptured ? "语音" : record.autoCaptured ? "自动" : "";
  const iconColor = record.type === "expense" ? channel.color : "var(--green)";
  const iconBg = record.type === "expense" ? channel.bg : "var(--green-pale)";
  const sourcePackage = String(record.sourcePackage || "").trim().toLowerCase();
  const sourceBrandIcon =
    sourcePackage === "com.tencent.mm"
      ? paymentBrandIconMarkup("wechat")
      : sourcePackage === "com.eg.android.alipaygphone"
        ? paymentBrandIconMarkup("alipay")
        : "";
  const channelBrandIcon = record.sourcePackage ? "" : paymentBrandIconMarkup(record.channel);
  const brandIcon = sourceBrandIcon || channelBrandIcon;
  const appIcon = brandIcon ? "" : appIconForPackage(sourcePackage);
  const iconMarkup = brandIcon || (appIcon
    ? `
      <span class="record-icon app-icon" aria-label="${escapeHtml(channel.name)}应用图标">
        <img src="${appIcon}" alt="" />
      </span>
    `
    : `
      <span class="record-icon" style="--icon-color:${iconColor};--icon-bg:${iconBg}">
        ${escapeHtml(getCategoryIcon(record.category, record.type))}
      </span>
    `);
  return `
    <button class="record-row" type="button" data-record-id="${record.id}" aria-label="编辑 ${escapeHtml(record.category)} ${formatMoney(record.amount)}">
      ${iconMarkup}
      <span class="record-copy">
        <span class="record-title-line">
          <strong>${escapeHtml(record.note || record.category)}</strong>
          <span class="channel-tag">${sourceBadge ? `${sourceBadge} · ` : ""}${escapeHtml(channel.name)}</span>
        </span>
        <p>
          ${escapeHtml(record.category)}
          ${options.showDate ? ` · ${escapeHtml(formatDateHeading(record.date))}` : ""}
        </p>
      </span>
      <span class="record-amount ${record.type}">
        ${record.type === "income" ? "+" : "−"}${formatMoney(record.amount)}
      </span>
    </button>
  `;
}

function paymentBrandIconMarkup(channelId) {
  if (channelId === "wechat") {
    return `
      <span class="record-icon payment-brand-icon wechat" role="img" aria-label="微信支付图标">
        <img src="wechat.svg" alt="" />
      </span>
    `;
  }
  if (channelId === "alipay") {
    return `
      <span class="record-icon payment-brand-icon alipay" role="img" aria-label="支付宝图标">
        <img src="alipay.svg" alt="" />
      </span>
    `;
  }
  return "";
}

function appIconForPackage(packageName) {
  if (!packageName || !isAndroidApp() || !window.AndroidLedger.getAppIcon) return "";
  const normalizedPackage = String(packageName).trim().toLowerCase();
  if (appIconCache.has(normalizedPackage)) return appIconCache.get(normalizedPackage);
  let icon = "";
  try {
    icon = window.AndroidLedger.getAppIcon(normalizedPackage) || "";
    if (!icon.startsWith("data:image/png;base64,")) icon = "";
  } catch (error) {
    console.warn("无法读取应用图标", error);
  }
  appIconCache.set(normalizedPackage, icon);
  return icon;
}

function bindRecordButtons(container) {
  container.querySelectorAll(".record-row").forEach((button) => {
    button.addEventListener("click", () => {
      const record = records.find((item) => item.id === button.dataset.recordId);
      if (record) openRecordDialog(record);
    });
  });
}

function renderRecords() {
  const list = getDisplayRecords();
  dom.recordCount.textContent = `${list.length} 笔`;
  if (!list.length) {
    const hasFilters = dom.searchInput.value.trim() || dom.channelFilter.value !== "all";
    const title = hasFilters ? "没有找到匹配记录" : showAllRecords ? "还没有任何记录" : "这个月还没有记录";
    const description = hasFilters
      ? "换个关键词或支付渠道试试。"
      : "从一笔早餐或一次工资开始，慢慢看见自己的收支轨迹。";
    dom.recordsList.innerHTML = `
      <div class="empty-state">
        <div>
          <span class="empty-state-icon">
            <svg viewBox="0 0 32 32" aria-hidden="true">
              <path d="M9 5.5h14a3 3 0 0 1 3 3v18l-5-3-5 3-5-3-5 3v-18a3 3 0 0 1 3-3Z"/>
              <path d="M11 12h10M11 17h7"/>
            </svg>
          </span>
          <h3>${title}</h3>
          <p>${description}</p>
          ${
            hasFilters
              ? '<button class="secondary-button clear-filters" type="button">清除筛选</button>'
              : '<button class="primary-button empty-add" type="button">记下第一笔</button>'
          }
        </div>
      </div>
    `;
    dom.recordsList.querySelector(".clear-filters")?.addEventListener("click", () => {
      dom.searchInput.value = "";
      dom.channelFilter.value = "all";
      renderRecords();
    });
    dom.recordsList.querySelector(".empty-add")?.addEventListener("click", () => openRecordDialog());
    return;
  }

  const grouped = new Map();
  for (const record of list) {
    if (!grouped.has(record.date)) grouped.set(record.date, []);
    grouped.get(record.date).push(record);
  }

  dom.recordsList.innerHTML = [...grouped.entries()]
    .map(([dateKey, dayRecords]) => {
      const summary = summarize(dayRecords);
      return `
        <section class="day-group">
          <div class="day-head">
            <div class="day-main">
              <strong>${formatDateHeading(dateKey)}</strong>
              <span>${weekdayName(dateKey)} · ${dateKey.slice(5).replace("-", "/")}</span>
            </div>
            <div class="day-total">
              ${summary.income ? `<span>收 <b>${formatMoney(summary.income)}</b></span>` : ""}
              ${summary.expense ? `<span>支 <b>${formatMoney(summary.expense)}</b></span>` : ""}
            </div>
          </div>
          ${dayRecords.map((record) => recordRowMarkup(record)).join("")}
        </section>
      `;
    })
    .join("");

  bindRecordButtons(dom.recordsList);
}

function renderChannels(periodRecords = getMonthRecords(), periodLabel = "本月") {
  const expenses = periodRecords.filter((record) => record.type === "expense");
  const total = expenses.reduce((sum, record) => sum + record.amount, 0);
  const grouped = CHANNELS.map((channel) => ({
    ...channel,
    amount: roundMoney(
      expenses
        .filter((record) => record.channel === channel.id)
        .reduce((sum, record) => sum + record.amount, 0),
    ),
  }))
    .filter((channel) => channel.amount > 0)
    .sort((a, b) => b.amount - a.amount);

  dom.donutTotal.textContent = formatCompactMoney(total);
  dom.channelPeriodLabel.textContent = periodLabel;
  if (!grouped.length) {
    dom.channelDonut.style.setProperty("--segments", "var(--paper-deep)");
    dom.channelLegend.innerHTML =
      `<p class="channel-empty">${escapeHtml(periodLabel)}有支出后，这里会显示各支付渠道的占比。</p>`;
    return;
  }

  let current = 0;
  const segments = grouped.map((channel, index) => {
    const start = current;
    current += (channel.amount / total) * 100;
    return `${DONUT_COLORS[index % DONUT_COLORS.length]} ${start}% ${current}%`;
  });
  dom.channelDonut.style.setProperty("--segments", segments.join(", "));

  dom.channelLegend.innerHTML = grouped
    .map((channel, index) => {
      const percent = Math.round((channel.amount / total) * 100);
      return `
        <div class="channel-legend-item">
          <i style="background:${DONUT_COLORS[index % DONUT_COLORS.length]}"></i>
          <span>${escapeHtml(channel.name)}</span>
          <b>${percent}%</b>
        </div>
      `;
    })
    .join("");
}

function renderInsight(periodRecords = getMonthRecords(), periodLabel = "本月") {
  if (!periodRecords.length) {
    dom.insightText.textContent = `${periodLabel}还没有记录，记下第一笔开始了解自己的消费吧。`;
    dom.insightAction.textContent = "记下第一笔";
    return;
  }

  const expenses = periodRecords.filter((record) => record.type === "expense");
  if (!expenses.length) {
    dom.insightText.textContent =
      `${periodLabel}已记录 ${formatMoney(summarize(periodRecords).income)} 收入，继续保持这份清晰。`;
    dom.insightAction.textContent = "补记一笔支出";
    return;
  }

  const categoryTotals = new Map();
  for (const expense of expenses) {
    categoryTotals.set(expense.category, (categoryTotals.get(expense.category) || 0) + expense.amount);
  }
  const top = [...categoryTotals.entries()].sort((a, b) => b[1] - a[1])[0];
  const totalExpense = summarize(expenses).expense;
  const percent = Math.round((top[1] / totalExpense) * 100);
  dom.insightText.textContent =
    `${periodLabel}「${top[0]}」花得最多，共 ${formatMoney(top[1])}，占支出的 ${percent}%。`;
  dom.insightAction.textContent = "查看对应明细";
}

function renderManageInfo() {
  dom.totalRecordCount.textContent = records.length;
}

function setRecordType(type, category) {
  dom.recordType.value = type;
  document.querySelectorAll(".type-switch button").forEach((button) => {
    const active = button.dataset.type === type;
    button.classList.toggle("active", active);
    button.classList.toggle("expense", active && type === "expense");
    button.classList.toggle("income", active && type === "income");
  });
  renderCategoryOptions(type, category || CATEGORY_GROUPS[type][0].name);
}

function renderCategoryOptions(type, selected, shouldValidate = false) {
  const categories = CATEGORY_GROUPS[type];
  const selectedName = categories.some((item) => item.name === selected) ? selected : categories[0].name;
  dom.categoryInput.value = selectedName;
  dom.categoryOptions.innerHTML = categories
    .map(
      (category) => `
        <button
          class="category-option ${category.name === selectedName ? "active" : ""}"
          type="button"
          data-category="${escapeHtml(category.name)}"
          aria-pressed="${category.name === selectedName}"
        >
          <span aria-hidden="true">${escapeHtml(category.icon)}</span>
          <span>${escapeHtml(category.name)}</span>
        </button>
      `,
    )
    .join("");
  dom.categoryOptions.querySelectorAll(".category-option").forEach((button) => {
    button.addEventListener("click", () => {
      dom.categoryInput.value = button.dataset.category;
      renderCategoryOptions(type, button.dataset.category, shouldValidate);
    });
  });
}

function openRecordDialog(record = null) {
  dom.recordForm.reset();
  dom.amountError.textContent = "";
  const isEditing = Boolean(record?.id);
  const type = record?.type || "expense";
  dom.recordId.value = record?.id || "";
  dom.dialogTitle.textContent = isEditing ? "编辑记录" : record ? "语音记账" : "记一笔";
  dom.amountInput.value = record?.amount || "";
  dom.dateInput.value = record?.date || todayForInput();
  dom.channelInput.value = record?.channel || "wechat";
  dom.noteInput.value = record?.note || "";
  dom.deleteRecordButton.hidden = !isEditing;
  setRecordType(type, record?.category);
  dom.recordDialog.showModal();
  window.setTimeout(() => dom.amountInput.focus(), 60);
}

function closeRecordDialog() {
  if (dom.recordDialog.open) dom.recordDialog.close();
}

function todayForInput() {
  return localDateKey(new Date());
}

function normalizeVoiceText(text) {
  return String(text || "")
    .replace(/[０-９]/g, (value) => String.fromCharCode(value.charCodeAt(0) - 0xfee0))
    .replace(/[：]/g, ":")
    .replace(/\s+/g, " ")
    .trim();
}

function spokenNumber(value) {
  const raw = String(value || "").replace(/,/g, "").trim();
  if (/^\d+(?:\.\d{1,2})?$/.test(raw)) return Number(raw);
  const digits = { 零: 0, 〇: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
  const units = { 十: 10, 百: 100, 千: 1000, 万: 10000 };
  const parts = raw.split("点");
  let total = 0;
  let section = 0;
  let number = 0;
  for (const char of parts[0]) {
    if (char in digits) {
      number = digits[char];
    } else if (char in units) {
      const unit = units[char];
      if (unit === 10000) {
        total += (section + number) * unit;
        section = 0;
      } else {
        section += (number || 1) * unit;
      }
      number = 0;
    }
  }
  let result = total + section + number;
  if (parts[1]) {
    const fraction = parts[1]
      .split("")
      .map((char) => digits[char] ?? Number(char))
      .join("");
    result += Number(`0.${fraction}`);
  }
  return Number.isFinite(result) ? result : 0;
}

function extractVoiceAmounts(text) {
  const pattern =
    /([0-9]+(?:[.,][0-9]{1,2})?|[零〇一二两三四五六七八九十百千万点]+)\s*(元|块钱|块|圆)([零〇一二两三四五六七八九0-9])?/g;
  return [...text.matchAll(pattern)].map((match) => {
    let amount = spokenNumber(match[1]);
    if (match[3] && !String(match[1]).includes(".")) {
      amount += Number(`0.${spokenNumber(match[3])}`);
    }
    return roundMoney(amount);
  }).filter((amount) => amount > 0);
}

function voiceDate(text) {
  const date = new Date();
  let hasDate = false;
  if (text.includes("大前天")) {
    date.setDate(date.getDate() - 3);
    hasDate = true;
  } else if (text.includes("前天")) {
    date.setDate(date.getDate() - 2);
    hasDate = true;
  } else if (text.includes("昨天")) {
    date.setDate(date.getDate() - 1);
    hasDate = true;
  } else if (text.includes("今天")) {
    hasDate = true;
  }
  const matchedDate = text.match(/(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?/);
  if (matchedDate) {
    const candidate = new Date(
      date.getFullYear(),
      Number(matchedDate[1]) - 1,
      Number(matchedDate[2]),
    );
    if (!Number.isNaN(candidate.getTime())) return localDateKey(candidate);
  }
  return hasDate ? localDateKey(date) : "";
}

function voiceCategory(text, type) {
  if (type === "income") {
    if (/工资|薪资|薪水/.test(text)) return "工资";
    if (/奖金|年终/.test(text)) return "奖金";
    if (/退款|退回/.test(text)) return "退款";
    if (/红包/.test(text)) return "红包";
    if (/理财|利息|分红/.test(text)) return "理财";
    if (/报销/.test(text)) return "报销";
    if (/兼职/.test(text)) return "兼职";
    return "收款";
  }
  if (/餐|饭|早餐|午餐|晚餐|外卖|咖啡|奶茶|面包/.test(text)) return "餐饮";
  if (/地铁|公交|打车|滴滴|加油|停车|高铁|火车|机票/.test(text)) return "交通";
  if (/淘宝|京东|购物|超市|买|商场|便利店/.test(text)) return "购物";
  if (/房租|水费|电费|燃气|物业|宽带/.test(text)) return "居住";
  if (/电影|游戏|视频|音乐|演出|会员/.test(text)) return "娱乐";
  if (/医院|药|挂号|体检|诊所/.test(text)) return "医疗";
  if (/课程|书店|培训|学习/.test(text)) return "学习";
  if (/话费|流量|通讯/.test(text)) return "通讯";
  if (/礼|红包/.test(text)) return "人情";
  return "其他";
}

function parseVoiceClause(text, fallbackDate) {
  const amounts = extractVoiceAmounts(text);
  let amount = amounts[0] || 0;
  if (!amount) {
    const bare = [...text.matchAll(/(?<![\d.])(\d+(?:\.\d{1,2})?)(?![\d.])/g)]
      .map((match) => Number(match[1]))
      .filter((value) => value > 0 && value < 1_000_000);
    if (bare.length === 1 && /花|消费|支付|付款|收到|收入|支出|收款|到账|买/.test(text)) {
      amount = bare[0];
    }
  }
  if (!amount) return null;
  const incomeCue = /收到|收入|到账|收款|工资|奖金|退款|退回|报销|红包|赚了/.test(text);
  const expenseCue = /花|消费|支付|付款|买|支出|扣款|打车|吃|交费|房租/.test(text);
  const type = incomeCue && !expenseCue ? "income" : "expense";
  return {
    id: "",
    type,
    amount: roundMoney(amount),
    date: voiceDate(text) || fallbackDate,
    channel: "other",
    category: voiceCategory(text, type),
    note: text.slice(0, 40),
    createdAt: new Date().toISOString(),
    voiceCaptured: true,
    sourceLabel: "语音记账",
  };
}

function parseVoiceTransactions(text) {
  const clean = normalizeVoiceText(text);
  if (!clean) return [];
  const overallDate = voiceDate(clean) || todayForInput();
  const totalAmounts = extractVoiceAmounts(clean);
  const clauses = clean
    .split(/[，,。；;、\n]+|然后|还有|另外|以及/)
    .map((item) => item.trim())
    .filter(Boolean);
  if (totalAmounts.length > 1 && clauses.length > 1) {
    return clauses.map((clause) => parseVoiceClause(clause, overallDate)).filter(Boolean);
  }
  const parsed = parseVoiceClause(clean, overallDate);
  return parsed ? [parsed] : [];
}

function finishVoiceListening() {
  window.clearTimeout(voiceStopTimer);
  voiceStopTimer = null;
  voiceHolding = false;
  voiceSessionActive = false;
  voicePressPointerId = null;
  dom.voiceHoldHud.hidden = true;
  dom.voiceHoldHud.classList.remove("recognizing");
  dom.navVoiceButton.classList.remove("listening");
  dom.navVoiceButton.disabled = false;
  dom.navVoiceButton.setAttribute("aria-busy", "false");
}

function showVoiceResults(text, items) {
  voiceTranscriptText = text;
  voiceDraft = items;
  dom.voiceTranscript.textContent = `“${text}”`;
  dom.voiceConfirmButton.disabled = items.length === 0;
  if (items.length) {
    dom.voiceResults.innerHTML = items
      .map(
        (item) => `
          <div class="voice-result">
            <div class="voice-result-copy">
              <strong>${escapeHtml(item.note || item.category)}</strong>
              <span>${item.type === "income" ? "收入" : "支出"} · ${escapeHtml(item.category)} · ${escapeHtml(formatDateHeading(item.date))}</span>
            </div>
            <span class="voice-result-amount">${item.type === "income" ? "+" : "−"}${formatMoney(item.amount)}</span>
          </div>
        `,
      )
      .join("");
    dom.voiceNote.textContent = `识别到 ${items.length} 笔记录，请确认金额和分类后再导入。`;
  } else {
    dom.voiceResults.innerHTML =
      '<div class="voice-empty">没有从这句话中识别出明确金额。可以重新说一遍，或进入手动调整。</div>';
    dom.voiceNote.textContent = "建议说：“今天午餐32元”或“收到工资8000元”。";
  }
  dom.voiceDialog.showModal();
}

function handleVoiceResult(payload) {
  finishVoiceListening();
  let result;
  try {
    result = typeof payload === "string" ? JSON.parse(payload) : payload;
  } catch {
    result = { ok: false, message: "语音识别结果格式异常" };
  }
  if (!result?.ok) {
    if (result?.needsCloudSettings) {
      openCloudSpeechSettingsDialog();
      showToast(result.message || "请先配置腾讯云语音识别");
      return;
    }
    const message = result?.message || "语音识别失败，请使用手动记账";
    if (!dom.voiceDialog.open) dom.voiceDialog.showModal();
    dom.voiceTranscript.textContent = "未能完成语音识别";
    dom.voiceResults.innerHTML =
      `<div class="voice-empty">${escapeHtml(message)}</div>`;
    dom.voiceNote.textContent = "请按住麦克风重新说，或进入手动调整。";
    dom.voiceConfirmButton.disabled = true;
    showToast(message);
    return;
  }
  const text = normalizeVoiceText(result.text);
  showVoiceResults(text, parseVoiceTransactions(text));
}

function handleVoicePartial(text) {
  if (!voiceSessionActive) return;
  const clean = normalizeVoiceText(text);
  if (!clean) return;
  dom.voiceHoldTranscript.textContent = clean;
}

function beginVoiceHold() {
  if (voiceSessionActive) return;
  voiceSessionActive = true;
  voiceHolding = true;
  voiceDraft = [];
  voiceTranscriptText = "";
  webSpeechFinalText = "";
  dom.voiceHoldHud.hidden = false;
  dom.voiceHoldHud.classList.remove("recognizing");
  dom.voiceHoldTitle.textContent = "正在接听…";
  dom.voiceHoldTranscript.textContent = "按住说话，松开结束";
  dom.navVoiceButton.setAttribute("aria-busy", "true");
  dom.navVoiceButton.classList.add("listening");
  if (isAndroidApp()) {
    try {
      window.AndroidLedger.startVoiceRecognition();
    } catch {
      finishVoiceListening();
      handleVoiceResult({ ok: false, message: "无法启动语音识别，请使用手动记账" });
    }
    return;
  }
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    finishVoiceListening();
    handleVoiceResult({ ok: false, message: "当前浏览器不支持语音识别，请使用手动调整" });
    return;
  }
  try {
    webSpeechRecognition = new Recognition();
    webSpeechRecognition.lang = "zh-CN";
    webSpeechRecognition.continuous = true;
    webSpeechRecognition.interimResults = true;
    webSpeechRecognition.maxAlternatives = 3;
    webSpeechRecognition.onresult = (event) => {
      let transcript = "";
      for (let index = 0; index < event.results.length; index++) {
        transcript += event.results[index]?.[0]?.transcript || "";
      }
      webSpeechFinalText = transcript;
      handleVoicePartial(transcript);
    };
    webSpeechRecognition.onerror = () => {
      handleVoiceResult({ ok: false, message: "语音识别失败，请使用手动记账" });
    };
    webSpeechRecognition.onend = () => {
      if (!voiceSessionActive) return;
      if (webSpeechFinalText) {
        handleVoiceResult({ ok: true, text: webSpeechFinalText, message: "识别完成" });
      } else {
        handleVoiceResult({ ok: false, message: "没有听清，请重新按住按钮说话" });
      }
    };
    webSpeechRecognition.start();
  } catch {
    finishVoiceListening();
    showToast("无法启动语音识别，请使用手动记账");
  }
}

function endVoiceHold() {
  if (!voiceSessionActive || !voiceHolding) return;
  voiceHolding = false;
  dom.navVoiceButton.classList.remove("listening");
  dom.voiceHoldHud.classList.add("recognizing");
  dom.voiceHoldTitle.textContent = "正在识别…";
  if (!dom.voiceHoldTranscript.textContent || dom.voiceHoldTranscript.textContent === "按住说话，松开结束") {
    dom.voiceHoldTranscript.textContent = "已松开按钮，正在整理语音内容";
  }
  try {
    if (isAndroidApp()) window.AndroidLedger.stopVoiceRecognition?.();
    else webSpeechRecognition?.stop();
  } catch {
    handleVoiceResult({ ok: false, message: "结束语音输入失败，请重试" });
    return;
  }
  window.clearTimeout(voiceStopTimer);
  voiceStopTimer = window.setTimeout(() => {
    if (voiceSessionActive) {
      handleVoiceResult({ ok: false, message: "语音识别超时，请重新按住按钮说话" });
    }
  }, 35_000);
}

function handleVoicePressStart(event) {
  if (event.type === "pointerdown" && event.button !== undefined && event.button !== 0) return;
  if (voiceSessionActive || voicePressPointerId !== null) return;
  event.preventDefault();
  voicePressPointerId = event.pointerId ?? "keyboard";
  if (event.pointerId !== undefined) {
    try {
      dom.navVoiceButton.setPointerCapture(event.pointerId);
    } catch {
      // Pointer capture is optional; window-level release still ends the recording.
    }
  }
  beginVoiceHold();
}

function handleVoicePressEnd(event) {
  if (voicePressPointerId === null) return;
  if (event?.pointerId !== undefined && event.pointerId !== voicePressPointerId) return;
  event?.preventDefault?.();
  const wasHolding = voiceSessionActive && voiceHolding;
  voicePressPointerId = null;
  if (wasHolding) {
    endVoiceHold();
  } else if (!voiceSessionActive) {
    showToast("请按住麦克风说话，松开结束");
  }
}

function handleVoiceKeyDown(event) {
  if (event.key !== " " && event.key !== "Enter") return;
  if (event.repeat) {
    event.preventDefault();
    return;
  }
  handleVoicePressStart(event);
}

function handleVoiceKeyUp(event) {
  if (event.key !== " " && event.key !== "Enter") return;
  handleVoicePressEnd(event);
}

function importVoiceDraft() {
  if (!voiceDraft.length) return;
  const imported = voiceDraft.map((item) => normalizeRecord({
    ...item,
    id: uid(),
    createdAt: new Date().toISOString(),
  }));
  records.push(...imported);
  selectedMonth = imported[0].date.slice(0, 7);
  saveRecords();
  dom.voiceDialog.close();
  renderAll();
  switchView("dashboard");
  showToast(`已通过语音导入 ${imported.length} 笔记录`);
}

function openVoiceManualEntry() {
  dom.voiceDialog.close();
  const draft = voiceDraft[0]
    ? { ...voiceDraft[0], id: "" }
    : { date: todayForInput(), note: voiceTranscriptText };
  openRecordDialog(draft);
}

function handleRecordSubmit(event) {
  event.preventDefault();
  const amount = Number(dom.amountInput.value);
  if (!Number.isFinite(amount) || amount <= 0) {
    dom.amountError.textContent = "请输入大于 0 的金额";
    dom.amountInput.focus();
    return;
  }
  if (!dom.dateInput.value) {
    showToast("请选择记录日期");
    dom.dateInput.focus();
    return;
  }

  const existing = records.find((record) => record.id === dom.recordId.value);
  const sourceMetadata = existing
    ? {
        autoCaptured: existing.autoCaptured,
        voiceCaptured: existing.voiceCaptured,
        sourceLabel: existing.sourceLabel,
        sourcePackage: existing.sourcePackage,
        confidence: existing.confidence,
        explanation: existing.explanation,
      }
    : {};
  const newRecord = {
    id: existing?.id || uid(),
    type: dom.recordType.value,
    amount: roundMoney(amount),
    date: dom.dateInput.value,
    channel: dom.channelInput.value,
    category: dom.categoryInput.value,
    note: dom.noteInput.value.trim(),
    createdAt: existing?.createdAt || new Date().toISOString(),
    ...sourceMetadata,
  };

  if (existing) {
    records = records.map((record) => (record.id === existing.id ? newRecord : record));
  } else {
    records.push(newRecord);
    selectedMonth = newRecord.date.slice(0, 7);
    showAllRecords = false;
  }
  saveRecords();
  closeRecordDialog();
  renderAll();
  showToast(existing ? "记录已更新" : newRecord.type === "income" ? "收入已记下" : "支出已记下");
}

function requestDeleteRecord() {
  const record = records.find((item) => item.id === dom.recordId.value);
  if (!record) return;
  openConfirm({
    title: "删除这笔记录？",
    message: `${record.date} · ${record.category} · ${formatMoney(record.amount)}，删除后无法恢复。`,
    actionText: "删除记录",
    onConfirm: () => {
      records = records.filter((item) => item.id !== record.id);
      if (record.autoCaptured) dismissAutoRecord(record.id);
      saveRecords();
      closeRecordDialog();
      renderAll();
      showToast("记录已删除");
    },
  });
}

function getDismissedAutoIds() {
  try {
    const values = JSON.parse(localStorage.getItem(DISMISSED_AUTO_KEY) || "[]");
    return new Set(Array.isArray(values) ? values : []);
  } catch {
    return new Set();
  }
}

function dismissAutoRecord(id) {
  const dismissed = getDismissedAutoIds();
  dismissed.add(id);
  localStorage.setItem(DISMISSED_AUTO_KEY, JSON.stringify([...dismissed].slice(-500)));
}

function openConfirm({ title, message, actionText, onConfirm }) {
  dom.confirmTitle.textContent = title;
  dom.confirmMessage.textContent = message;
  dom.confirmActionButton.textContent = actionText;
  pendingConfirm = onConfirm;
  dom.confirmDialog.showModal();
}

function closeConfirm() {
  pendingConfirm = null;
  if (dom.confirmDialog.open) dom.confirmDialog.close();
}

function openMonthPicker() {
  const [selectedYear, selectedMonthNumber] = selectedMonth.split("-").map(Number);
  const currentYear = new Date().getFullYear();
  const years = new Set([currentYear, selectedYear]);
  records.forEach((record) => years.add(Number(record.date.slice(0, 4))));
  dom.yearSelect.innerHTML = [...years]
    .sort((a, b) => b - a)
    .map((year) => `<option value="${year}" ${year === selectedYear ? "selected" : ""}>${year} 年</option>`)
    .join("");
  dom.monthSelect.innerHTML = Array.from({ length: 12 }, (_, index) => {
    const month = index + 1;
    return `<option value="${month}" ${month === selectedMonthNumber ? "selected" : ""}>${month} 月</option>`;
  }).join("");
  dom.monthDialog.showModal();
}

function applyMonthSelection(event) {
  event.preventDefault();
  analyticsMode = "month";
  selectedMonth = `${dom.yearSelect.value}-${String(dom.monthSelect.value).padStart(2, "0")}`;
  showAllRecords = false;
  dom.monthLabel.textContent = formatMonthLabel(selectedMonth);
  dom.searchInput.value = "";
  dom.channelFilter.value = "all";
  dom.monthDialog.close();
  renderAll();
}

function updateMonthLabel() {
  dom.monthLabel.textContent = formatMonthLabel(selectedMonth);
  dom.heroPeriod.textContent = selectedMonth.replace("-", " / ");
}

function exportRecords() {
  if (!records.length) {
    showToast("暂无可导出的记录");
    return;
  }
  const rows = [
    ["日期", "类型", "金额", "支付渠道", "分类", "备注", "记录时间"],
    ...[...records]
      .sort((a, b) => a.date.localeCompare(b.date) || a.createdAt.localeCompare(b.createdAt))
      .map((record) => [
        record.date,
        record.type === "income" ? "收入" : "支出",
        Number(record.amount).toFixed(2),
        getChannel(record.channel).name,
        record.category,
        record.note,
        record.createdAt,
      ]),
  ];
  const csv = "\uFEFF" + rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  if (isAndroidApp()) {
    const result = window.AndroidLedger.saveCsv(`日迹账本_${todayForInput()}.csv`, csv);
    showToast(result || "已导出账单");
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `日迹账本_${todayForInput()}.csv`;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  showToast(`已导出 ${records.length} 条记录`);
}

function csvCell(value) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let inQuotes = false;
  const source = text.replace(/^\uFEFF/, "");

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    const next = source[index + 1];
    if (char === '"' && inQuotes && next === '"') {
      cell += '"';
      index += 1;
    } else if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === "," && !inQuotes) {
      row.push(cell);
      cell = "";
    } else if ((char === "\n" || char === "\r") && !inQuotes) {
      if (char === "\r" && next === "\n") index += 1;
      row.push(cell);
      if (row.some((value) => value.trim())) rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += char;
    }
  }
  row.push(cell);
  if (row.some((value) => value.trim())) rows.push(row);
  return rows;
}

async function importCsv(file) {
  try {
    const text = await file.text();
    const rows = parseCsv(text);
    if (rows.length < 2) throw new Error("文件中没有可导入的记录");
    const headers = rows[0].map((header) => header.trim());
    const findColumn = (...names) => headers.findIndex((header) => names.includes(header));
    const dateIndex = findColumn("日期", "date");
    const typeIndex = findColumn("类型", "type");
    const amountIndex = findColumn("金额", "amount");
    const channelIndex = findColumn("支付渠道", "渠道", "channel");
    const categoryIndex = findColumn("分类", "category");
    const noteIndex = findColumn("备注", "note");

    if ([dateIndex, typeIndex, amountIndex, channelIndex, categoryIndex].includes(-1)) {
      throw new Error("CSV 缺少日期、类型、金额、支付渠道或分类列");
    }

    const imported = [];
    const existingIds = new Set(records.map((record) => record.id));
    for (const row of rows.slice(1)) {
      const date = normalizeImportedDate(row[dateIndex]);
      const typeText = String(row[typeIndex] || "").trim();
      const type = typeText.includes("收") || typeText.toLowerCase() === "income" ? "income" : "expense";
      const amount = Number(String(row[amountIndex] || "").replace(/[¥,\s]/g, ""));
      const channelName = String(row[channelIndex] || "").trim();
      const channel = CHANNELS.find((item) => item.name === channelName)?.id || "other";
      const category = String(row[categoryIndex] || "").trim();
      const note = noteIndex >= 0 ? String(row[noteIndex] || "").trim() : "";
      if (!date || !Number.isFinite(amount) || amount <= 0) continue;
      let id = uid();
      while (existingIds.has(id)) id = uid();
      existingIds.add(id);
      imported.push(
        normalizeRecord({
          id,
          type,
          amount,
          date,
          channel,
          category,
          note,
          createdAt: new Date().toISOString(),
        }),
      );
    }
    if (!imported.length) throw new Error("没有找到有效的记录");
    records = [...records, ...imported];
    saveRecords();
    renderAll();
    showToast(`已导入 ${imported.length} 条记录`);
  } catch (error) {
    showToast(error.message || "导入失败，请检查 CSV 格式");
  } finally {
    dom.importInput.value = "";
  }
}

function normalizeImportedDate(value) {
  const text = String(value || "").trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text;
  const match = text.match(/^(\d{4})[/年.](\d{1,2})[/月.](\d{1,2})日?$/);
  if (!match) return "";
  return `${match[1]}-${match[2].padStart(2, "0")}-${match[3].padStart(2, "0")}`;
}

function loadDemoData() {
  openConfirm({
    title: "载入示例记录？",
    message: "会添加 14 条本月示例记录，保留你已有的数据，方便体验统计和筛选。",
    actionText: "载入示例",
    onConfirm: () => {
      const now = new Date();
      const dateAt = (daysAgo) => {
        const date = new Date(now);
        date.setDate(date.getDate() - daysAgo);
        return localDateKey(date);
      };
      const samples = [
        ["income", 12800, dateAt(12), "bank", "工资", "本月工资"],
        ["expense", 26.5, dateAt(6), "wechat", "餐饮", "早餐和咖啡"],
        ["expense", 218, dateAt(5), "alipay", "购物", "日用品补货"],
        ["expense", 12, dateAt(5), "wechat", "交通", "地铁"],
        ["expense", 3800, dateAt(4), "bank", "居住", "房租"],
        ["income", 168, dateAt(4), "alipay", "退款", "退货退款"],
        ["expense", 49, dateAt(3), "wechat", "通讯", "手机套餐"],
        ["expense", 89, dateAt(3), "unionpay", "娱乐", "电影票"],
        ["expense", 43.8, dateAt(2), "alipay", "餐饮", "晚饭"],
        ["expense", 22, dateAt(2), "wechat", "交通", "打车"],
        ["expense", 199, dateAt(1), "wechat", "学习", "电子书会员"],
        ["expense", 36.9, dateAt(1), "alipay", "餐饮", "轻食"],
        ["income", 600, dateAt(1), "wechat", "兼职", "周末设计稿"],
        ["expense", 62, dateAt(0), "alipay", "购物", "家庭清洁用品"],
      ];
      records.push(
        ...samples.map(([type, amount, date, channel, category, note], index) =>
          normalizeRecord({
            id: uid(),
            type,
            amount,
            date,
            channel,
            category,
            note,
            createdAt: new Date(Date.now() - index * 60_000).toISOString(),
          }),
        ),
      );
      selectedMonth = toMonthKey(new Date());
      saveRecords();
      renderAll();
      closeManageDialog();
      showToast("示例记录已载入");
    },
  });
}

function clearAllRecords() {
  if (!records.length) {
    showToast("目前没有记录");
    return;
  }
  openConfirm({
    title: "清空全部记录？",
    message: `即将删除全部 ${records.length} 条记录，操作无法撤销。建议先导出备份。`,
    actionText: "清空记录",
    onConfirm: () => {
      records = [];
      try {
        window.AndroidLedger?.clearCapturedTransactions();
      } catch (error) {
        console.warn("无法清理手机自动记录", error);
      }
      saveRecords();
      closeManageDialog();
      renderAll();
      showToast("全部记录已清空");
    },
  });
}

function isAndroidApp() {
  return Boolean(window.AndroidLedger);
}

function readNativePermissionStatus() {
  if (!isAndroidApp()) return null;
  try {
    return JSON.parse(window.AndroidLedger.getPermissionStatus());
  } catch (error) {
    console.warn("无法读取自动记账权限", error);
    return null;
  }
}

function updateNativePermissionUi() {
  if (!isAndroidApp()) {
    dom.nativePermissionPanel.hidden = true;
    return;
  }

  dom.nativePermissionPanel.hidden = false;
  refreshAiPanelStatus();
  refreshCloudSpeechPanelStatus();
  dom.manageStorageNote.textContent =
    "你的数据只保存在这台 Android 手机上。建议定期导出备份，换手机时再导入。";
  const status = readNativePermissionStatus() || {};
  const notificationReady = Boolean(status.notificationAccess);
  const smsReady = Boolean(status.smsGranted);

  dom.notificationPermissionButton.textContent = notificationReady ? "已开启" : "去开启";
  dom.notificationPermissionButton.classList.toggle("active", notificationReady);
  dom.smsPermissionButton.textContent = smsReady ? "已开启" : "去授权";
  dom.smsPermissionButton.classList.toggle("active", smsReady);

  if (notificationReady && smsReady) {
    dom.permissionPill.textContent = "已开启";
    dom.permissionPill.classList.add("active");
  } else if (notificationReady) {
    dom.permissionPill.textContent = "部分开启";
    dom.permissionPill.classList.remove("active");
  } else {
    dom.permissionPill.textContent = "未开启";
    dom.permissionPill.classList.remove("active");
  }
}

function readAiSettings() {
  if (!isAndroidApp()) return null;
  try {
    return JSON.parse(window.AndroidLedger.getAiSettings() || "{}");
  } catch (error) {
    console.warn("无法读取 AI 设置", error);
    return null;
  }
}

function refreshAiPanelStatus(settings = null) {
  if (!isAndroidApp() || !dom.aiPanelStatus) return;
  settings = settings || readAiSettings();
  if (settings?.mode === "local") {
    dom.aiPanelStatus.textContent = "仅本地规则 · 不调用模型";
    return;
  }
  if (!settings?.enabled) {
    dom.aiPanelStatus.textContent = "未启用 · 本地规则优先";
    return;
  }
  if (!settings.hasApiKey || !settings.consent) {
    dom.aiPanelStatus.textContent = "已启用但配置不完整";
    return;
  }
  const mode = settings.mode === "local" ? "仅本地规则" : "AI 优先";
  dom.aiPanelStatus.textContent = `DeepSeek · ${settings.model || "deepseek-chat"} · ${mode}`;
}

function populateDeepSeekModels(models = [], selected = "deepseek-chat") {
  const options = { ...DEEPSEEK_MODELS };
  for (const id of models) {
    if (typeof id === "string" && id.trim()) options[id] = id;
  }
  if (selected && !options[selected]) options[selected] = selected;
  dom.aiModel.innerHTML = Object.entries(options)
    .map(([id, label]) => `<option value="${escapeHtml(id)}">${escapeHtml(label)}</option>`)
    .join("");
  dom.aiModel.value = options[selected] ? selected : Object.keys(options)[0];
}

function openAiSettingsDialog() {
  if (!isAndroidApp()) return;
  const settings = readAiSettings();
  if (!settings) {
    showToast("无法读取 AI 设置");
    return;
  }
  dom.aiMode.value = settings.mode === "local" ? "local" : "hybrid";
  dom.aiEndpoint.value = settings.endpoint || DEEPSEEK_ENDPOINT;
  populateDeepSeekModels([], settings.model || "deepseek-chat");
  dom.aiApiKey.value = "";
  dom.aiApiKey.placeholder = settings.hasApiKey
    ? "API Key 已加密保存，留空表示不修改"
    : "填写你从服务商申请的 API Key";
  dom.aiEnabled.checked = Boolean(settings.enabled);
  dom.aiConsent.checked = Boolean(settings.consent);
  dom.aiSettingsStatus.textContent = "";
  dom.aiSettingsStatus.classList.remove("error");
  dom.testAiConnectionButton.disabled = false;
  dom.refreshDeepSeekModelsButton.disabled = false;
  dom.aiSettingsDialog.showModal();
}

function aiSettingsPayload() {
  return {
    enabled: dom.aiEnabled.checked,
    consent: dom.aiConsent.checked,
    provider: "deepseek",
    endpoint: dom.aiEndpoint.value.trim(),
    model: dom.aiModel.value.trim(),
    mode: dom.aiMode.value,
    apiKey: dom.aiApiKey.value.trim(),
  };
}

function saveAiSettings(showFeedback = true) {
  if (!isAndroidApp()) return null;
  let result;
  try {
    result = JSON.parse(
      window.AndroidLedger.saveAiSettings(JSON.stringify(aiSettingsPayload())),
    );
  } catch (error) {
    result = { ok: false, message: "保存 AI 设置失败" };
  }
  dom.aiSettingsStatus.textContent = result.message || "";
  dom.aiSettingsStatus.classList.toggle("error", !result.ok);
  if (result.ok) {
    dom.aiApiKey.value = "";
    if (result.settings?.hasApiKey) {
      dom.aiApiKey.placeholder = "API Key 已加密保存，留空表示不修改";
    }
    refreshAiPanelStatus(result.settings);
    if (showFeedback) showToast(result.message);
  }
  return result;
}

function testAiConnection() {
  const saved = saveAiSettings(false);
  if (!saved?.ok) return;
  dom.testAiConnectionButton.disabled = true;
  dom.aiSettingsStatus.textContent = "正在连接 DeepSeek 并测试收支判断…";
  dom.aiSettingsStatus.classList.remove("error");
  window.AndroidLedger.testAiConnection();
}

function refreshDeepSeekModels() {
  const saved = saveAiSettings(false);
  if (!saved?.ok) return;
  dom.refreshDeepSeekModelsButton.disabled = true;
  dom.aiSettingsStatus.textContent = "正在从 DeepSeek 获取可用模型…";
  dom.aiSettingsStatus.classList.remove("error");
  window.AndroidLedger.listDeepSeekModels();
}

function readCloudSpeechSettings() {
  if (!isAndroidApp()) return null;
  try {
    return JSON.parse(window.AndroidLedger.getCloudSpeechSettings() || "{}");
  } catch (error) {
    console.warn("无法读取云语音设置", error);
    return null;
  }
}

function refreshCloudSpeechPanelStatus(settings = null) {
  if (!isAndroidApp() || !dom.cloudVoicePanelStatus) return;
  settings = settings || readCloudSpeechSettings() || {};
  if (!settings.enabled) {
    dom.cloudVoicePanelStatus.textContent = "腾讯云 · 尚未启用";
  } else if (!settings.hasSecretId || !settings.hasSecretKey || !settings.consent) {
    dom.cloudVoicePanelStatus.textContent = "腾讯云 · 配置不完整";
  } else {
    dom.cloudVoicePanelStatus.textContent = "腾讯云 · 一句话识别已就绪";
  }
}

function openCloudSpeechSettingsDialog() {
  if (!isAndroidApp()) return;
  const settings = readCloudSpeechSettings();
  if (!settings) {
    showToast("无法读取云语音设置");
    return;
  }
  dom.cloudSpeechEngine.value = settings.engine || "16k_zh";
  dom.cloudSpeechSecretId.value = "";
  dom.cloudSpeechSecretKey.value = "";
  dom.cloudSpeechSecretId.placeholder = settings.hasSecretId
    ? "SecretId 已加密保存，留空表示不修改"
    : "填写腾讯云访问密钥 SecretId";
  dom.cloudSpeechSecretKey.placeholder = settings.hasSecretKey
    ? "SecretKey 已加密保存，留空表示不修改"
    : "填写腾讯云访问密钥 SecretKey";
  dom.cloudSpeechEnabled.checked = Boolean(settings.enabled);
  dom.cloudSpeechConsent.checked = Boolean(settings.consent);
  dom.cloudSpeechSettingsStatus.textContent = "";
  dom.cloudSpeechSettingsStatus.classList.remove("error");
  if (!dom.cloudSpeechSettingsDialog.open) dom.cloudSpeechSettingsDialog.showModal();
}

function cloudSpeechSettingsPayload() {
  return {
    enabled: dom.cloudSpeechEnabled.checked,
    consent: dom.cloudSpeechConsent.checked,
    engine: dom.cloudSpeechEngine.value,
    secretId: dom.cloudSpeechSecretId.value.trim(),
    secretKey: dom.cloudSpeechSecretKey.value.trim(),
  };
}

function saveCloudSpeechSettings(showFeedback = true) {
  if (!isAndroidApp()) return null;
  let result;
  try {
    result = JSON.parse(
      window.AndroidLedger.saveCloudSpeechSettings(
        JSON.stringify(cloudSpeechSettingsPayload()),
      ),
    );
  } catch (error) {
    result = { ok: false, message: "保存云语音设置失败" };
  }
  dom.cloudSpeechSettingsStatus.textContent = result.message || "";
  dom.cloudSpeechSettingsStatus.classList.toggle("error", !result.ok);
  if (result.ok) {
    dom.cloudSpeechSecretId.value = "";
    dom.cloudSpeechSecretKey.value = "";
    if (result.settings?.hasSecretId) {
      dom.cloudSpeechSecretId.placeholder = "SecretId 已加密保存，留空表示不修改";
    }
    if (result.settings?.hasSecretKey) {
      dom.cloudSpeechSecretKey.placeholder = "SecretKey 已加密保存，留空表示不修改";
    }
    refreshCloudSpeechPanelStatus(result.settings);
    if (showFeedback) showToast(result.message);
  }
  return result;
}

function mergeNativeTransactions() {
  if (!isAndroidApp()) return 0;
  let nativeTransactions = [];
  try {
    nativeTransactions = JSON.parse(window.AndroidLedger.getCapturedTransactions() || "[]");
  } catch (error) {
    console.warn("无法读取手机自动记录", error);
    return 0;
  }
  if (!Array.isArray(nativeTransactions)) return 0;

  const existingIds = new Set(records.map((record) => record.id));
  const dismissedIds = getDismissedAutoIds();
  let added = 0;
  for (const transaction of nativeTransactions) {
    if (!isValidRecord(transaction) || existingIds.has(transaction.id) || dismissedIds.has(transaction.id)) {
      continue;
    }
    records.push(normalizeRecord({ ...transaction, autoCaptured: true }));
    existingIds.add(transaction.id);
    added += 1;
  }
  if (added) {
    saveRecords();
    renderAll();
  }
  return added;
}

function refreshNativeIntegration() {
  if (!isAndroidApp()) return;
  mergeNativeTransactions();
  updateNativePermissionUi();
}

function renderNotificationDiagnostics() {
  if (!isAndroidApp()) return;
  const status = readNativePermissionStatus() || {};
  let events = [];
  try {
    events = JSON.parse(window.AndroidLedger.getNotificationDiagnostics() || "[]");
  } catch (error) {
    console.warn("无法读取通知诊断", error);
  }
  events = Array.isArray(events) ? events : [];
  const relevantEvents = events;

  if (!status.notificationAccess) {
    dom.diagnosticSummary.textContent =
      "通知读取权限尚未开启。请返回自动记账权限，开启「通知使用权」后再支付一笔测试。";
  } else if (!relevantEvents.length) {
    dom.diagnosticSummary.textContent =
      "已获得通知权限，但授权后还没有收到微信、支付宝或银行的通知。请确认对应 App 允许发送通知。";
  } else {
    const last = relevantEvents.at(-1);
    dom.diagnosticSummary.textContent = last.recognized
      ? `最近一次「${last.appLabel || last.packageName}」通知已通过本地语义识别。`
      : last.containedAmount
        ? "已收到支付相关通知并找到金额，但交易状态或收支方向还不够明确。请核对下方语义分析。"
        : "已收到支付相关通知，但通知正文没有金额。该通知无法生成可靠账目。";
  }

  if (!relevantEvents.length) {
    dom.diagnosticList.innerHTML =
      '<div class="diagnostic-empty">还没有捕获到支付相关通知。自动记录只覆盖授权后出现的通知。</div>';
    return;
  }

  dom.diagnosticList.innerHTML = [...relevantEvents]
    .reverse()
    .map((event) => {
      const largeScore = Number(event.icon?.largeIconSimilarity ?? -1);
      const smallScore = Number(event.icon?.smallIconSimilarity ?? -1);
      const bestIconScore = Math.max(largeScore, smallScore);
      const iconText =
        bestIconScore >= 0 ? `图标相似度 ${bestIconScore}%` : "图标无法比较";
      return `
        <article class="diagnostic-event">
          <div class="diagnostic-event-head">
            <span>${escapeHtml(event.capturedAt || "未知时间")}</span>
            <span class="diagnostic-status ${event.recognized ? "recognized" : "unrecognized"}">
              ${event.recognized ? "已识别" : event.containedAmount ? "方向不明确" : "未包含金额"}
            </span>
          </div>
          <div class="diagnostic-event-meta">
            <span>${escapeHtml(event.appLabel || event.packageName || "未知应用")} · ${escapeHtml(event.packageName || "")}</span>
            <span>${escapeHtml(iconText)}</span>
          </div>
          <strong>${escapeHtml(event.title || "支付通知")}</strong>
          <p>${escapeHtml(event.body || "通知正文为空")}</p>
          ${
            event.semanticReason
              ? `<p class="diagnostic-reason">${escapeHtml(event.semanticReason)}</p>`
              : ""
          }
        </article>
      `;
    })
    .join("");
}

function openNotificationDiagnostics() {
  if (!isAndroidApp()) return;
  renderNotificationDiagnostics();
  dom.diagnosticDialog.showModal();
}

function openManageDialog() {
  renderManageInfo();
  dom.manageDialog.showModal();
}

function closeManageDialog() {
  if (dom.manageDialog.open) dom.manageDialog.close();
}

function showToast(message) {
  window.clearTimeout(toastTimer);
  dom.toast.textContent = message;
  dom.toast.classList.add("show");
  toastTimer = window.setTimeout(() => dom.toast.classList.remove("show"), 2200);
}

function switchView(viewName) {
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === viewName);
  });
  const targetId =
    viewName === "records"
      ? "recordsView"
      : viewName === "analytics"
        ? "analyticsView"
        : "dashboardView";
  document.querySelectorAll(".page-view").forEach((view) => {
    view.hidden = view.id !== targetId;
    view.classList.toggle("is-active", view.id === targetId);
  });
  if (viewName === "records") {
    showAllRecords = true;
    dom.searchInput.value = "";
    dom.channelFilter.value = "all";
    renderRecords();
  } else if (viewName === "analytics") {
    renderAnalytics();
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function initialize() {
  dom.channelInput.innerHTML = CHANNELS.map(
    (channel) => `<option value="${channel.id}">${channel.name}</option>`,
  ).join("");
  dom.channelFilter.innerHTML += CHANNELS.map(
    (channel) => `<option value="${channel.id}">${channel.name}</option>`,
  ).join("");

  const now = new Date();
  const dateLabel = `${now.getMonth() + 1} 月 ${now.getDate()} 日 · ${weekdayName(localDateKey(now))}`;
  dom.todayLabel.textContent = `${dateLabel} · 认真记录，轻松掌握收支`;
  dom.topbarDate.textContent = dateLabel;
  updateMonthLabel();
  setRecordType("expense", "餐饮");
  renderAll();

  document.querySelector("#quickAddButton").addEventListener("click", () => openRecordDialog());
  dom.todayManualAddButton.addEventListener("click", () => openRecordDialog());
  dom.navVoiceButton.addEventListener("pointerdown", handleVoicePressStart);
  dom.navVoiceButton.addEventListener("keydown", handleVoiceKeyDown);
  dom.navVoiceButton.addEventListener("keyup", handleVoiceKeyUp);
  dom.navVoiceButton.addEventListener("contextmenu", (event) => event.preventDefault());
  window.addEventListener("pointerup", handleVoicePressEnd);
  document.querySelectorAll(".type-switch button").forEach((button) => {
    button.addEventListener("click", () => setRecordType(button.dataset.type));
  });
  dom.recordForm.addEventListener("submit", handleRecordSubmit);
  document.querySelector("#closeDialogButton").addEventListener("click", closeRecordDialog);
  document.querySelector("#cancelButton").addEventListener("click", closeRecordDialog);
  dom.deleteRecordButton.addEventListener("click", requestDeleteRecord);
  dom.monthButton.addEventListener("click", openMonthPicker);
  dom.monthDialog.querySelector("form").addEventListener("submit", applyMonthSelection);
  document.querySelector("#viewAllButton").addEventListener("click", () => switchView("records"));
  dom.searchInput.addEventListener("input", renderRecords);
  dom.channelFilter.addEventListener("change", renderRecords);
  document.querySelector("#exportButton").addEventListener("click", exportRecords);
  document.querySelector("#profileButton").addEventListener("click", openManageDialog);
  document.querySelector("#closeManageButton").addEventListener("click", closeManageDialog);
  document.querySelector("#manageExportButton").addEventListener("click", exportRecords);
  document.querySelector("#importButton").addEventListener("click", () => dom.importInput.click());
  dom.importInput.addEventListener("change", () => {
    const file = dom.importInput.files?.[0];
    if (file) importCsv(file);
  });
  document.querySelector("#demoButton").addEventListener("click", loadDemoData);
  document.querySelector("#clearButton").addEventListener("click", clearAllRecords);
  document.querySelector("#confirmCancelButton").addEventListener("click", closeConfirm);
  dom.confirmActionButton.addEventListener("click", () => {
    const action = pendingConfirm;
    closeConfirm();
    action?.();
  });
  document.querySelectorAll(".nav-item[data-view]").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
  });
  document.querySelector("#navProfileButton").addEventListener("click", openManageDialog);
  dom.insightAction.addEventListener("click", () => {
    const expenses = getAnalyticsPeriod().records.filter((record) => record.type === "expense");
    if (!expenses.length) {
      openRecordDialog();
      return;
    }
    const categoryTotals = new Map();
    for (const expense of expenses) {
      categoryTotals.set(expense.category, (categoryTotals.get(expense.category) || 0) + expense.amount);
    }
    const topCategory = [...categoryTotals.entries()].sort((a, b) => b[1] - a[1])[0][0];
    switchView("records");
    dom.searchInput.value = topCategory;
    renderRecords();
  });
  document.querySelectorAll("[data-period]").forEach((button) => {
    button.addEventListener("click", () => {
      analyticsMode = button.dataset.period === "month" ? "month" : "week";
      renderAnalytics();
    });
  });
  dom.notificationPermissionButton.addEventListener("click", () => {
    window.AndroidLedger?.openNotificationSettings();
  });
  dom.smsPermissionButton.addEventListener("click", () => {
    window.AndroidLedger?.requestSmsPermission();
  });
  dom.notificationDiagnosticButton.addEventListener("click", openNotificationDiagnostics);
  document.querySelector("#closeDiagnosticButton").addEventListener("click", () => {
    dom.diagnosticDialog.close();
  });
  document.querySelector("#refreshDiagnosticButton").addEventListener("click", () => {
    refreshNativeIntegration();
    renderNotificationDiagnostics();
  });
  document.querySelector("#clearDiagnosticButton").addEventListener("click", () => {
    window.AndroidLedger?.clearNotificationDiagnostics();
    renderNotificationDiagnostics();
  });
  document.querySelector("#closeVoiceButton").addEventListener("click", () => {
    dom.voiceDialog.close();
  });
  dom.voiceDialog.addEventListener("close", () => {
    finishVoiceListening();
    window.AndroidLedger?.cancelVoiceRecognition?.();
    try {
      webSpeechRecognition?.abort();
    } catch {
      // The browser speech session may already have ended.
    }
    webSpeechRecognition = null;
  });
  dom.voiceRetryButton.addEventListener("click", () => {
    dom.voiceDialog.close();
    showToast("请按住底部麦克风重新说");
  });
  dom.voiceManualButton.addEventListener("click", openVoiceManualEntry);
  dom.voiceConfirmButton.addEventListener("click", importVoiceDraft);
  dom.aiSettingsButton.addEventListener("click", openAiSettingsDialog);
  document.querySelector("#closeAiSettingsButton").addEventListener("click", () => {
    dom.aiSettingsDialog.close();
  });
  dom.refreshDeepSeekModelsButton.addEventListener("click", refreshDeepSeekModels);
  dom.testAiConnectionButton.addEventListener("click", testAiConnection);
  dom.aiSettingsForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const result = saveAiSettings(true);
    if (result?.ok) window.setTimeout(() => dom.aiSettingsDialog.close(), 280);
  });
  dom.cloudSpeechSettingsButton.addEventListener("click", openCloudSpeechSettingsDialog);
  document.querySelector("#closeCloudSpeechSettingsButton").addEventListener("click", () => {
    dom.cloudSpeechSettingsDialog.close();
  });
  dom.cloudSpeechSettingsForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const result = saveCloudSpeechSettings(true);
    if (result?.ok) {
      window.setTimeout(() => dom.cloudSpeechSettingsDialog.close(), 280);
    }
  });

  window.loadNativeTransactions = () => {
    refreshNativeIntegration();
  };
  window.onAndroidPermissionChanged = () => {
    refreshNativeIntegration();
  };
  window.onVoiceResult = handleVoiceResult;
  window.onVoicePartial = handleVoicePartial;
  window.onAiTestResult = (payload) => {
    let result;
    try {
      result = typeof payload === "string" ? JSON.parse(payload) : payload;
    } catch {
      result = { ok: false, message: "模型测试返回格式异常" };
    }
    dom.testAiConnectionButton.disabled = false;
    dom.aiSettingsStatus.textContent = result.message || (result.ok ? "连接成功" : "连接失败");
    dom.aiSettingsStatus.classList.toggle("error", !result.ok);
    showToast(result.message || (result.ok ? "DeepSeek 连接成功" : "DeepSeek 连接失败"));
  };
  window.onDeepSeekModelsResult = (payload) => {
    let result;
    try {
      result = typeof payload === "string" ? JSON.parse(payload) : payload;
    } catch {
      result = { ok: false, message: "模型列表返回格式异常" };
    }
    dom.refreshDeepSeekModelsButton.disabled = false;
    dom.aiSettingsStatus.textContent = result.message || "模型列表已更新";
    dom.aiSettingsStatus.classList.toggle("error", !result.ok);
    if (result.ok && Array.isArray(result.models) && result.models.length) {
      populateDeepSeekModels(result.models, dom.aiModel.value);
    }
  };
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refreshNativeIntegration();
  });
  window.addEventListener("focus", refreshNativeIntegration);

  dom.recordDialog.addEventListener("click", (event) => {
    if (event.target !== dom.recordDialog) return;
    const rect = dom.recordDialog.getBoundingClientRect();
    const inside =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom;
    if (!inside) closeRecordDialog();
  });

  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("service-worker.js").catch((error) => {
      console.warn("离线缓存注册失败", error);
    });
  }

  refreshNativeIntegration();
}

initialize();
