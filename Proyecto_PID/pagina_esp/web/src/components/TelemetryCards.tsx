'use client';

import React from 'react';
import { Target, Gauge, AlertCircle, Zap } from 'lucide-react';
import { TelemetryData } from '../types/telemetry';

interface TelemetryCardsProps {
  telemetry: TelemetryData | null;
}

export const TelemetryCards: React.FC<TelemetryCardsProps> = ({ telemetry }) => {
  const setpoint = telemetry?.setpoint ?? 0;
  const position = telemetry?.position ?? 0;
  const error = telemetry?.error ?? 0;
  const pwm = telemetry?.pwm ?? 0;

  const isHighError = Math.abs(error) > 15;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-5">
      
      {/* 1. Setpoint Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-5 flex flex-col justify-between relative overflow-hidden shadow-lg shadow-black/20">
        <div className="flex items-center justify-between text-slate-400 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider">Target position</span>
          <div className="p-2 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
            <Target className="w-4 h-4" />
          </div>
        </div>
        <div>
          <div className="flex items-baseline space-x-2">
            <span className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight font-mono">
              {setpoint.toFixed(1)}
            </span>
            <span className="text-lg sm:text-xl font-medium text-white">°</span>
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Page reference</p>
        </div>
      </div>

      {/* 2. Measured Position Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-5 flex flex-col justify-between relative overflow-hidden shadow-lg shadow-black/20">
        <div className="flex items-center justify-between text-slate-400 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider">Current position</span>
          <div className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Gauge className="w-4 h-4" />
          </div>
        </div>
        <div>
          <div className="flex items-baseline space-x-2">
            <span className="text-2xl sm:text-4xl font-extrabold text-emerald-400 tracking-tight font-mono">
              {position.toFixed(1)}
            </span>
            <span className="text-xs sm:text-sm font-medium text-slate-400 uppercase">DEG</span>
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Analog model input (GPIO 34)</p>
        </div>
      </div>

      {/* 3. PID Error Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-5 flex flex-col justify-between relative overflow-hidden shadow-lg shadow-black/20">
        <div className="flex items-center justify-between text-slate-400 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider">Error</span>
          <div className={`p-2 rounded-xl border ${
            isHighError
              ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
              : 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20'
          }`}>
            <AlertCircle className="w-4 h-4" />
          </div>
        </div>
        <div>
          <div className="flex items-baseline space-x-2">
            <span className={`text-2xl sm:text-4xl font-extrabold tracking-tight font-mono ${
              isHighError ? 'text-amber-400' : 'text-slate-200'
            }`}>
              {error >= 0 ? `+${error.toFixed(1)}` : error.toFixed(1)}
            </span>
            <span className="text-xs sm:text-sm font-medium text-slate-400 uppercase">DEG</span>
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Setpoint - Measured</p>
        </div>
      </div>

      {/* 4. PWM / Control Output Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-5 flex flex-col justify-between relative overflow-hidden shadow-lg shadow-black/20">
        <div className="flex items-center justify-between text-slate-400 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider">DAC Output</span>
          <div className="p-2 rounded-xl bg-violet-500/10 text-violet-400 border border-violet-500/20">
            <Zap className="w-4 h-4" />
          </div>
        </div>
        <div>
          <div className="flex items-baseline space-x-2">
            <span className="text-2xl sm:text-4xl font-extrabold text-violet-400 tracking-tight font-mono">
              {pwm.toFixed(1)}
            </span>
            <span className="text-xs sm:text-sm font-medium text-slate-400">%</span>
          </div>
          {/* Mini Progress Bar */}
          <div className="w-full bg-slate-800 rounded-full h-1.5 mt-2 overflow-hidden">
            <div
              className="bg-gradient-to-r from-violet-500 to-indigo-400 h-1.5 rounded-full transition-all duration-150"
              style={{ width: `${Math.min(100, Math.abs(pwm))}%` }}
            />
          </div>
        </div>
      </div>

    </div>
  );
};
