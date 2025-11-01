
import React, { useCallback, useRef } from 'react';
import type { Zone } from '../types';
import { MIN_VALUE, MAX_STROKES, MAX_SETS, MIN_SPM, MAX_SPM, SPM_GREEN_THRESHOLD, SPM_YELLOW_THRESHOLD } from '../constants';
import Icon from './Icon';

interface ZoneBlockProps {
  zone: Zone;
  index: number;
  onUpdate: (id: string, updatedValues: Partial<Zone>) => void;
  onRemove: (id: string) => void;
  onDuplicate: (id: string) => void;
  onAddAfter: (id: string) => void;
  isDuplicateDisabled?: boolean;
  isLastZone?: boolean;
  // Drag and drop props
  isDragging: boolean;
  isDragOver: boolean;
  onDragStart: () => void;
  onDragEnter: () => void;
  onDragLeave: () => void;
  onDragOver: (e: React.DragEvent<HTMLDivElement>) => void;
  onDrop: () => void;
  onDragEnd: () => void;
}

interface ControlProps {
  label: string;
  value: number;
  onIncrement: () => void;
  onDecrement: () => void;
}

const Control: React.FC<ControlProps> = ({ label, value, onIncrement, onDecrement }) => (
  <div className="flex flex-col items-center space-y-2">
    <span className="text-lg font-bold text-white/80 uppercase tracking-wider">{label}</span>
    <div className="flex items-center space-x-4">
      <button onClick={onDecrement} className="p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors duration-200">
        <Icon type="minus" className="w-5 h-5" />
      </button>
      <span className="text-4xl font-bold text-white w-12 text-center">{value}</span>
      <button onClick={onIncrement} className="p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors duration-200">
        <Icon type="plus" className="w-5 h-5" />
      </button>
    </div>
  </div>
);

const getZoneColor = (spm: number): string => {
  if (spm <= SPM_GREEN_THRESHOLD) {
    return 'from-zone-green-start to-zone-green-end';
  }
  if (spm <= SPM_YELLOW_THRESHOLD) {
    return 'from-zone-yellow-start to-zone-yellow-end';
  }
  return 'from-zone-red-start to-zone-red-end';
};

export const ZoneBlock: React.FC<ZoneBlockProps> = ({ 
  zone, 
  index, 
  onUpdate, 
  onRemove, 
  onDuplicate,
  onAddAfter,
  isDuplicateDisabled,
  isLastZone,
  isDragging,
  isDragOver,
  onDragStart,
  onDragEnter,
  onDragLeave,
  onDragOver,
  onDrop,
  onDragEnd,
}) => {
  const divRef = useRef<HTMLDivElement>(null);

  const internalDragLeaveHandler = (e: React.DragEvent<HTMLDivElement>) => {
    // This check prevents the drag leave event from firing when the cursor moves over child elements.
    // The ring will stay visible as long as the cursor is anywhere inside the ZoneBlock.
    if (divRef.current && !divRef.current.contains(e.relatedTarget as Node)) {
      onDragLeave();
    }
  };
  
  const handleValueChange = useCallback(<K extends keyof Zone,>(key: K, delta: number) => {
    const currentValue = zone[key] as number;
    let newValue = currentValue + delta;

    switch (key) {
      case 'strokes':
        newValue = Math.max(MIN_VALUE, Math.min(newValue, MAX_STROKES));
        break;
      case 'sets':
        newValue = Math.max(MIN_VALUE, Math.min(newValue, MAX_SETS));
        break;
      case 'spm':
        newValue = Math.max(MIN_SPM, Math.min(newValue, MAX_SPM));
        break;
    }

    if (newValue !== currentValue) {
      onUpdate(zone.id, { [key]: newValue });
    }
  }, [zone, onUpdate]);

  const draggingStyles = isDragging ? 'opacity-40 scale-95' : 'opacity-100 scale-100';
  const dragOverStyles = isDragOver ? 'ring-2 ring-accent-cyan ring-offset-2 ring-offset-charcoal' : '';

  return (
    <div 
      ref={divRef}
      draggable="true"
      onDragStart={onDragStart}
      onDragEnter={onDragEnter}
      onDragLeave={internalDragLeaveHandler}
      onDragOver={onDragOver}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
      className={`relative rounded-xl shadow-lg p-6 border border-white/20 bg-gradient-to-br ${getZoneColor(zone.spm)} transition-all duration-300 cursor-grab active:cursor-grabbing ${draggingStyles} ${dragOverStyles}`}
    >
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-2xl font-bold text-white">Zone {index + 1}</h3>
        <div className="flex items-center space-x-2">
          <button
            onClick={() => onAddAfter(zone.id)}
            className="flex items-center justify-center w-8 h-8 bg-accent-cyan text-white rounded-xl shadow-lg hover:bg-accent-cyan-hover transition-all duration-200 transform hover:scale-105 disabled:bg-gray-500 disabled:cursor-not-allowed disabled:scale-100"
            aria-label={`Add Zone After Zone ${index + 1}`}
            disabled={isDuplicateDisabled}
            title={isDuplicateDisabled ? "Maximum number of zones reached" : "Add New Zone After"}
          >
            <Icon type="plus" className="w-5 h-5" strokeWidth={2.5} />
          </button>
           <button
            onClick={() => onDuplicate(zone.id)}
            className="p-1.5 rounded-full text-white/70 hover:text-white hover:bg-white/10 transition-colors duration-200 disabled:text-white/30 disabled:hover:bg-transparent disabled:cursor-not-allowed"
            aria-label={`Duplicate Zone ${index + 1}`}
            disabled={isDuplicateDisabled}
            title={isDuplicateDisabled ? "Maximum number of zones reached" : "Duplicate Zone"}
          >
            <Icon type="copy" className="w-6 h-6" strokeWidth={2} />
          </button>
          <button
            onClick={() => onRemove(zone.id)}
            className="p-1.5 rounded-full text-white/70 hover:text-white hover:bg-white/10 transition-colors duration-200"
            aria-label={`Remove Zone ${index + 1}`}
          >
            <Icon type="trash" className="w-6 h-6" strokeWidth={2} />
          </button>
        </div>
      </div>
      
      <div className="space-y-6">
        <Control
          label="Strokes"
          value={zone.strokes}
          onIncrement={() => handleValueChange('strokes', 1)}
          onDecrement={() => handleValueChange('strokes', -1)}
        />
        <Control
          label="Sets"
          value={zone.sets}
          onIncrement={() => handleValueChange('sets', 1)}
          onDecrement={() => handleValueChange('sets', -1)}
        />
        <Control
          label="SPM"
          value={zone.spm}
          onIncrement={() => handleValueChange('spm', 1)}
          onDecrement={() => handleValueChange('spm', -1)}
        />
      </div>
      
      {isLastZone && (
        <div className="mt-6">
          <button
            className="w-full bg-accent-cyan text-white font-extrabold py-3 px-4 rounded-lg text-4xl uppercase tracking-wider shadow-lg hover:bg-accent-cyan-hover transition-all duration-300 transform hover:scale-105 animate-pulse-slow"
            onClick={() => console.log('Start Clicked!')}
          >
            Start
          </button>
        </div>
      )}
    </div>
  );
};
