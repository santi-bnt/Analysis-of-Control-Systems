'use client';

import React, { useState, useEffect } from 'react';
import { Minus, Plus, Send, RotateCcw, Sliders } from 'lucide-react';

interface SetpointControllerProps {
  currentSetpoint: number;
  onApplySetpoint: (newVal: number) => Promise<boolean>;
  minPosition?: number;
  maxPosition?: number;
  disabled?: boolean;
}

export const SetpointController: React.FC<SetpointControllerProps> = ({
  currentSetpoint,
  onApplySetpoint,
  minPosition = -180,
  maxPosition = 180,
  disabled = false,
}) => {
  const [inputValue, setInputValue] = useState<string>(currentSetpoint.toString());
  const [sliderValue, setSliderValue] = useState<number>(currentSetpoint);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; error?: boolean } | null>(null);

  // Sync internal state when external setpoint updates
  useEffect(() => {
    setInputValue(currentSetpoint.toString());
    setSliderValue(currentSetpoint);
  }, [currentSetpoint]);

  const handleStep = (delta: number) => {
    const current = parseFloat(inputValue) || 0;
    const nextVal = Math.min(maxPosition, Math.max(minPosition, current + delta));
    setInputValue(nextVal.toString());
    setSliderValue(nextVal);
  };

  const handleApply = async (valueToApply?: number) => {
    const target = valueToApply !== undefined ? valueToApply : parseFloat(inputValue);

    if (isNaN(target)) {
      setFeedbackMsg({ text: 'Enter a valid angle in degrees.', error: true });
      return;
    }

    if (target < minPosition || target > maxPosition) {
      setFeedbackMsg({
        text: `Position must be between ${minPosition}° and ${maxPosition}°`,
        error: true,
      });
      return;
    }

    setIsSubmitting(true);
    setFeedbackMsg(null);

    try {
      const success = await onApplySetpoint(target);
      if (success) {
        setFeedbackMsg({ text: `Target position applied: ${target}°`, error: false });
        setTimeout(() => setFeedbackMsg(null), 3000);
      } else {
        setFeedbackMsg({ text: 'Failed to apply setpoint. Check broker connection.', error: true });
      }
    } catch {
      setFeedbackMsg({ text: 'Error transmitting command.', error: true });
    } finally {
      setIsSubmitting(false);
    }
  };

  const presets = [-180, -90, 0, 90, 180];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-6 shadow-lg shadow-black/20">
      
      {/* Title */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <Sliders className="w-5 h-5 text-indigo-400" />
          <h2 className="text-base sm:text-lg font-semibold text-white">Position target control</h2>
        </div>
        <span className="text-xs text-slate-400 font-mono">
          Range: {minPosition}° to {maxPosition}°
        </span>
      </div>

      {/* Main stepper control */}
      <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 sm:p-4 mb-4 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center justify-center space-x-3 w-full sm:w-auto">
          {/* Minus Button */}
          <button
            type="button"
            onClick={() => handleStep(-10)}
            disabled={disabled || isSubmitting}
            className="w-12 h-12 rounded-xl bg-slate-800 hover:bg-slate-700 active:bg-slate-600 text-white flex items-center justify-center font-bold text-lg border border-slate-700 disabled:opacity-40 transition-colors cursor-pointer"
            title="Decrease 10 degrees"
          >
            <Minus className="w-5 h-5" />
          </button>

          {/* Stepper Display */}
          <div className="min-w-[130px] sm:min-w-[150px] px-4 py-2 bg-slate-900 border border-slate-700/80 rounded-xl text-center">
            <span className="text-2xl sm:text-3xl font-extrabold text-white font-mono tracking-tight">
              {sliderValue}
            </span>
            <span className="text-white ml-1.5 font-medium">°</span>
          </div>

          {/* Plus Button */}
          <button
            type="button"
            onClick={() => handleStep(10)}
            disabled={disabled || isSubmitting}
            className="w-12 h-12 rounded-xl bg-slate-800 hover:bg-slate-700 active:bg-slate-600 text-white flex items-center justify-center font-bold text-lg border border-slate-700 disabled:opacity-40 transition-colors cursor-pointer"
            title="Increase 10 degrees"
          >
            <Plus className="w-5 h-5" />
          </button>
        </div>

        {/* Manual Input & Apply Button */}
        <div className="flex items-center space-x-2 w-full sm:w-auto">
          <input
            type="number"
            min={minPosition}
            max={maxPosition}
            step="1"
            value={inputValue}
            onChange={(e) => {
              setInputValue(e.target.value);
              const num = parseFloat(e.target.value);
              if (!isNaN(num)) setSliderValue(num);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleApply();
            }}
            placeholder="Degrees"
            disabled={disabled || isSubmitting}
            className="w-full sm:w-28 px-3.5 py-2.5 bg-slate-900 border border-slate-700 rounded-xl text-white font-mono text-center focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 disabled:opacity-40"
          />

          <button
            type="button"
            onClick={() => handleApply()}
            disabled={disabled || isSubmitting}
            className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white rounded-xl font-semibold text-sm flex items-center justify-center space-x-1.5 shadow-md shadow-indigo-950 transition-all cursor-pointer disabled:opacity-50"
          >
            <Send className="w-4 h-4" />
            <span>{isSubmitting ? 'Sending...' : 'Apply'}</span>
          </button>
        </div>
      </div>

      {/* Slider for smooth touchscreen adjustment */}
      <div className="mb-4">
        <input
          type="range"
          min={minPosition}
          max={maxPosition}
          step="5"
          value={sliderValue}
          onChange={(e) => {
            const val = parseFloat(e.target.value);
            setSliderValue(val);
            setInputValue(val.toString());
          }}
          disabled={disabled || isSubmitting}
          className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-indigo-500"
        />
      </div>

      {/* Quick Presets */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-slate-400 font-medium mr-1">Presets:</span>
        {presets.map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => {
              setInputValue(preset.toString());
              setSliderValue(preset);
              handleApply(preset);
            }}
            disabled={disabled || isSubmitting}
            className={`px-3 py-1 rounded-lg text-xs font-mono font-medium border transition-colors cursor-pointer ${
              preset === 0
                ? 'bg-rose-950/40 text-rose-300 border-rose-800/60 hover:bg-rose-900/50'
                : 'bg-slate-800/80 text-slate-300 border-slate-700 hover:bg-slate-700'
            }`}
          >
            {preset === 0 ? 'HOME (0°)' : `${preset}°`}
          </button>
        ))}
      </div>

      {/* Feedback Message */}
      {feedbackMsg && (
        <div
          className={`mt-3 text-xs px-3 py-2 rounded-lg border ${
            feedbackMsg.error
              ? 'bg-rose-950/50 border-rose-800 text-rose-300'
              : 'bg-emerald-950/50 border-emerald-800 text-emerald-300'
          }`}
        >
          {feedbackMsg.text}
        </div>
      )}

    </div>
  );
};
