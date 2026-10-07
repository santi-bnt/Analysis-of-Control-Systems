'use client';

import React from 'react';
import { Wifi, WifiOff, Power, RotateCcw, Cpu } from 'lucide-react';

interface DeviceHeaderProps {
  deviceId: string;
  isOnline: boolean;
  wsConnected: boolean;
  isDemoMode: boolean;
  onEmergencyStop: () => void;
  onResetSafety: () => void;
  isEstopActive?: boolean;
}

export const DeviceHeader: React.FC<DeviceHeaderProps> = ({
  deviceId,
  isOnline,
  wsConnected,
  isDemoMode,
  onEmergencyStop,
  onResetSafety,
  isEstopActive = false,
}) => {
  return (
    <header className="w-full bg-slate-900 border-b border-slate-800 px-4 py-4 sm:px-6 sm:py-5 text-white">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        
        {/* Left: Device Info */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400">
            <Cpu className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-white">
                DC Motor Controller
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full font-mono bg-indigo-950 text-indigo-300 border border-indigo-800">
                {deviceId}
              </span>
            </div>
            <p className="text-xs sm:text-sm text-slate-400">
              {isDemoMode ? 'Local Motor Simulation' : 'Local Analog PID Model Control'}
            </p>
          </div>
        </div>

        {/* Right: Badges & E-Stop */}
        <div className="flex items-center flex-wrap gap-2.5 sm:gap-4 w-full sm:w-auto justify-between sm:justify-end">
          
          {/* Cloud WebSocket status */}
          <div className="flex items-center space-x-1.5 text-xs text-slate-400 bg-slate-800/80 px-2.5 py-1.5 rounded-lg border border-slate-700/60">
            <div className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-cyan-400 animate-pulse' : 'bg-rose-500'}`} />
            <span>{isDemoMode ? 'Local demo' : wsConnected ? 'Local Bridge' : 'Reconnecting...'}</span>
          </div>

          {/* Hardware ESP32 Online / Offline Status Badge */}
          <div className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold uppercase tracking-wider border transition-all ${
            isOnline 
              ? 'bg-emerald-950/80 text-emerald-400 border-emerald-500/40 shadow-sm shadow-emerald-900/30'
              : 'bg-rose-950/80 text-rose-400 border-rose-500/40 shadow-sm shadow-rose-900/30'
          }`}>
            {isOnline ? (
              <>
                <span className="relative flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                </span>
                <Wifi className="w-3.5 h-3.5" />
                <span>{isDemoMode ? 'SIMULATION' : 'ONLINE'}</span>
              </>
            ) : (
              <>
                <span className="h-2.5 w-2.5 rounded-full bg-rose-500"></span>
                <WifiOff className="w-3.5 h-3.5" />
                <span>{isDemoMode ? 'DEMO OFFLINE' : 'OFFLINE'}</span>
              </>
            )}
          </div>

          {/* Emergency Stop Button */}
          <button
            onClick={onEmergencyStop}
            className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg font-bold text-xs uppercase tracking-wider bg-red-600 hover:bg-red-500 active:bg-red-700 text-white shadow-md shadow-red-950 transition-all cursor-pointer border border-red-500"
            title="Immediate Emergency Motor Shutdown"
          >
            <Power className="w-3.5 h-3.5" />
            <span>E-STOP</span>
          </button>
          <button
            onClick={onResetSafety}
            className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg font-bold text-xs uppercase tracking-wider bg-slate-700 hover:bg-slate-600 text-white border border-slate-600"
            title="Clear a latched stop after checking the motor"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>RESET</span>
          </button>
        </div>

      </div>
    </header>
  );
};
