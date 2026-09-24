(function () {
  'use strict';

  /* ------------------------------------------------------------------
   * Theme tokens — read once from :root, reused by every chart so the
   * whole report shares one active chart palette (Business Blue).
   * ---------------------------------------------------------------- */
  var style = getComputedStyle(document.documentElement);
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var chartGrid = style.getPropertyValue('--chart-grid').trim();
  var chartAxis = style.getPropertyValue('--chart-axis').trim() || muted;
  var chartLabel = style.getPropertyValue('--chart-label').trim() || muted;
  var chartTooltipBg = style.getPropertyValue('--chart-tooltip-bg').trim() || bg2;
  var chartSeries = [
    style.getPropertyValue('--chart-series-1').trim(),
    style.getPropertyValue('--chart-series-2').trim(),
    style.getPropertyValue('--chart-series-3').trim(),
    style.getPropertyValue('--chart-series-4').trim()
  ];

  var axisLabel = { color: chartAxis, fontSize: 11 };
  var axisLine = { lineStyle: { color: chartGrid } };
  var splitLine = { lineStyle: { color: chartGrid, opacity: 0.5 } };

  function emptyState(el, message) {
    el.textContent = message;
    el.setAttribute('role', 'note');
    el.style.display = 'flex';
    el.style.alignItems = 'center';
    el.style.justifyContent = 'center';
    el.style.textAlign = 'center';
    el.style.padding = '24px';
    el.style.border = '1px dashed ' + chartGrid;
    el.style.borderRadius = '12px';
    el.style.color = muted;
    el.style.fontSize = '13px';
  }

  function mount(id, option, hasData) {
    var el = document.getElementById(id);
    if (!el) {
      return;
    }
    if (typeof echarts === 'undefined') {
      emptyState(el, '图表库未加载，数据以正文与表格为准。');
      return;
    }
    if (!hasData) {
      emptyState(el, '暂无可用数据。');
      return;
    }
    var chart = echarts.init(el, null, { renderer: 'svg' });
    chart.setOption(option);
    window.addEventListener('resize', function () { chart.resize(); });
  }

  function thousands(value) {
    return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  /* ------------------------------------------------------------------
   * Figure 1 — download counts per release, Modrinth vs CurseForge
   * Source: Modrinth version API + CurseForge file list (2026-09-24)
   * ---------------------------------------------------------------- */
  var versionLabels = ['0.1.0', '0.1.1', '0.2.0', '0.3.0', '0.4.1'];
  var modrinthData = [3, 1, 3, 52, 132];
  var curseforgeData = [44, 53, 196, 312, 197];

  mount('chart-versions', {
    color: [chartSeries[0], chartSeries[1]],
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: chartTooltipBg,
      borderColor: chartGrid,
      textStyle: { color: ink, fontSize: 12 }
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: muted, fontSize: 12 }
    },
    grid: { left: 8, right: 16, top: 16, bottom: 40, containLabel: true },
    xAxis: {
      type: 'category',
      name: '版本',
      nameTextStyle: { color: chartAxis, fontSize: 11 },
      nameGap: 8,
      data: versionLabels,
      axisLine: axisLine,
      axisTick: { show: false },
      axisLabel: axisLabel
    },
    yAxis: {
      type: 'value',
      name: '下载次数',
      nameTextStyle: { color: chartAxis, fontSize: 11 },
      axisLine: { show: false },
      axisLabel: axisLabel,
      splitLine: splitLine
    },
    series: [
      {
        name: 'Modrinth',
        type: 'bar',
        data: modrinthData,
        barMaxWidth: 26,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', color: muted, fontSize: 11 }
      },
      {
        name: 'CurseForge',
        type: 'bar',
        data: curseforgeData,
        barMaxWidth: 26,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', color: muted, fontSize: 11 }
      }
    ],
    animation: false
  }, modrinthData.length > 0 && curseforgeData.length > 0);

  /* ------------------------------------------------------------------
   * Figure 2 — addressable niche size (log scale)
   * Source: Modrinth project API, all fetched 2026-09-24
   * ---------------------------------------------------------------- */
  var nicheLabels = ['Colored Connections', 'Factory Controller', 'Extra Gauges', 'Vibrant Vaults'];
  var nicheData = [191, 9137, 502699, 896934];

  mount('chart-adjacent', {
    color: [chartSeries[0]],
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: chartTooltipBg,
      borderColor: chartGrid,
      textStyle: { color: ink, fontSize: 12 },
      formatter: function (params) {
        var point = params[0];
        return point.name + '：' + thousands(point.value) + ' 次下载';
      }
    },
    grid: { left: 8, right: 48, top: 16, bottom: 16, containLabel: true },
    xAxis: {
      type: 'value',
      name: '累计下载（对数刻度）',
      nameTextStyle: { color: chartAxis, fontSize: 11 },
      nameGap: 10,
      axisLine: { show: false },
      axisLabel: {
        color: chartAxis,
        fontSize: 11,
        formatter: function (value) {
          if (value >= 1000000) { return (value / 1000000) + 'M'; }
          if (value >= 1000) { return (value / 1000) + 'K'; }
          return value;
        }
      },
      splitLine: splitLine
    },
    yAxis: {
      type: 'category',
      data: nicheLabels,
      axisLine: axisLine,
      axisTick: { show: false },
      axisLabel: { color: chartLabel, fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: nicheData,
        barMaxWidth: 22,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: 'right',
          color: muted,
          fontSize: 11,
          formatter: function (params) { return thousands(params.value); }
        }
      }
    ],
    animation: false
  }, nicheData.length > 0);

  /* ------------------------------------------------------------------
   * Figure 3 — Create 1.21.1 release distribution (version concentration)
   * Source: Modrinth version API, fetched 2026-09-24 (all values verified)
   * ---------------------------------------------------------------- */
  var createLabels = ['6.0.0', '6.0.1', '6.0.2', '6.0.3', '6.0.4', '6.0.5', '6.0.6', '6.0.7', '6.0.8', '6.0.9', '6.0.10'];
  var createData = [47037, 31350, 37224, 23743, 332807, 25598, 714138, 91846, 428760, 1170879, 4990535];

  mount('chart-create-versions', {
    color: [chartSeries[0]],
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: chartTooltipBg,
      borderColor: chartGrid,
      textStyle: { color: ink, fontSize: 12 },
      formatter: function (params) {
        var point = params[0];
        return 'Create ' + point.name + '：' + thousands(point.value) + ' 次下载';
      }
    },
    grid: { left: 8, right: 24, top: 16, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      name: 'Create 版本（1.21.1 线）',
      nameTextStyle: { color: chartAxis, fontSize: 11 },
      nameGap: 8,
      data: createLabels,
      axisLine: axisLine,
      axisTick: { show: false },
      axisLabel: {
        color: chartAxis,
        fontSize: 11,
        interval: 0,
        rotate: 40
      }
    },
    yAxis: {
      type: 'value',
      name: '下载次数',
      nameTextStyle: { color: chartAxis, fontSize: 11 },
      axisLine: { show: false },
      axisLabel: {
        color: chartAxis,
        fontSize: 11,
        formatter: function (value) {
          if (value >= 1000000) { return (value / 1000000) + 'M'; }
          if (value >= 1000) { return (value / 1000) + 'K'; }
          return value;
        }
      },
      splitLine: splitLine
    },
    series: [
      {
        type: 'bar',
        data: createData,
        barMaxWidth: 26,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        label: {
          show: true,
          position: 'top',
          color: muted,
          fontSize: 10,
          rotate: 0,
          formatter: function (params) {
            return params.value >= 100000 ? (params.value / 10000).toFixed(0) + '万' : '';
          }
        }
      }
    ],
    animation: false
  }, createData.length > 0);
})();
