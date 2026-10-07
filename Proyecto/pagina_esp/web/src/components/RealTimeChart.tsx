'use client';

import React, { useMemo } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { Activity } from 'lucide-react';
import { ChartPoint } from '../types/telemetry';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

interface RealTimeChartProps {
  dataPoints: ChartPoint[];
  maxPoints?: number;
}

export const RealTimeChart: React.FC<RealTimeChartProps> = ({ dataPoints, maxPoints = 300 }) => {
  // Extract chart arrays
  const { labels, setpoints, positions } = useMemo(() => {
    // Only display every 2nd or 3rd label if dense to keep X-axis readable
    const sampleFactor = dataPoints.length > 100 ? 5 : 2;
    const lbls = dataPoints.map((pt, idx) => (idx % sampleFactor === 0 ? pt.timeLabel : ''));
    const sps = dataPoints.map((pt) => pt.setpoint);
    const positions = dataPoints.map((pt) => pt.position);

    return { labels: lbls, setpoints: sps, positions };
  }, [dataPoints]);

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Target position (°)',
        data: setpoints,
        borderColor: '#38bdf8', // Cyan-400
        backgroundColor: 'rgba(56, 189, 248, 0.05)',
        borderWidth: 2,
        borderDash: [6, 4],
        pointRadius: 0,
        tension: 0.1,
      },
      {
        label: 'Measured position (°)',
        data: positions,
        borderColor: '#10b981', // Emerald-500
        backgroundColor: 'transparent',
        borderWidth: 2.5,
        pointRadius: 0,
        tension: 0.15,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: 0, // Disable animation for high-frequency real-time updates to eliminate CPU load
    },
    interaction: {
      mode: 'index' as const,
      intersect: false,
    },
    plugins: {
      legend: {
        position: 'top' as const,
        align: 'end' as const,
        labels: {
          color: '#cbd5e1',
          font: {
            family: 'monospace',
            size: 11,
          },
          boxWidth: 16,
          boxHeight: 8,
          usePointStyle: false,
        },
      },
      tooltip: {
        backgroundColor: '#0f172a',
        titleColor: '#94a3b8',
        bodyColor: '#e2e8f0',
        borderColor: '#334155',
        borderWidth: 1,
        titleFont: { family: 'monospace', size: 10 },
        bodyFont: { family: 'monospace', size: 12 },
        padding: 10,
      },
    },
    scales: {
      x: {
        grid: {
          color: 'rgba(51, 65, 85, 0.3)',
        },
        ticks: {
          color: '#64748b',
          font: {
            family: 'monospace',
            size: 10,
          },
          maxRotation: 0,
          autoSkip: true,
        },
      },
      y: {
        grid: {
          color: 'rgba(51, 65, 85, 0.3)',
        },
        ticks: {
          color: '#94a3b8',
          font: {
            family: 'monospace',
            size: 10,
          },
        },
        suggestedMin: 0,
        suggestedMax: 200,
      },
    },
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-6 shadow-lg shadow-black/20 flex flex-col">
      {/* Chart Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 mb-2 border-b border-slate-800 gap-2">
        <div className="flex items-center space-x-2">
          <Activity className="w-5 h-5 text-emerald-400" />
          <h2 className="text-base sm:text-lg font-semibold text-white">Position tracking</h2>
          <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
            ~60s window
          </span>
        </div>
        <div className="flex items-center space-x-3 text-xs font-mono text-slate-400">
          <span className="flex items-center space-x-1.5">
            <span className="w-3 h-0.5 bg-sky-400 border-b border-dashed"></span>
            <span>Setpoint</span>
          </span>
          <span className="flex items-center space-x-1.5">
            <span className="w-3 h-1 bg-emerald-500 rounded"></span>
            <span>Measured</span>
          </span>
        </div>
      </div>

      {/* Chart Canvas */}
      <div className="w-full h-[280px] sm:h-[360px] relative">
        {dataPoints.length > 0 ? (
          <Line data={chartData} options={options} />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-slate-500">
            <Activity className="w-8 h-8 animate-pulse mb-2 text-slate-600" />
            <p className="text-sm">Awaiting incoming telemetry stream...</p>
          </div>
        )}
      </div>
    </div>
  );
};
