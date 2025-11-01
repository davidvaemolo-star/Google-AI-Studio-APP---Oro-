import React, { useState, useCallback, useMemo } from 'react';
import type { Zone, HapticDevice } from './types';
import { MAX_ZONES, MIN_SPM } from './constants';
import { ZoneBlock } from './components/ZoneBlock';
import Icon from './components/Icon';
import { ConnectionScreen } from './components/ConnectionScreen';
import { BottomNavBar } from './components/BottomNavBar';

const App: React.FC = () => {
  const [view, setView] = useState<'connection' | 'training'>('connection');
  const [devices, setDevices] = useState<HapticDevice[]>([]);
  const connectedDevicesCount = useMemo(() => devices.filter(d => d.status === 'connected').length, [devices]);

  const [zones, setZones] = useState<Zone[]>([]);
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);

  const handleAddZone = useCallback(() => {
    if (zones.length < MAX_ZONES) {
      const newZone: Zone = {
        id: `zone-${Date.now()}-${Math.random()}`,
        strokes: 10,
        sets: 1,
        spm: MIN_SPM,
      };
      setZones(prevZones => [...prevZones, newZone]);
    }
  }, [zones.length]);

  const handleUpdateZone = useCallback((id: string, updatedValues: Partial<Zone>) => {
    setZones(prevZones =>
      prevZones.map(zone =>
        zone.id === id ? { ...zone, ...updatedValues } : zone
      )
    );
  }, []);

  const handleRemoveZone = useCallback((id: string) => {
    setZones(prevZones => prevZones.filter(zone => zone.id !== id));
  }, []);
  
  const handleAddZoneAfter = useCallback((id: string) => {
    if (zones.length >= MAX_ZONES) {
      return;
    }

    const referenceZoneIndex = zones.findIndex(zone => zone.id === id);
    if (referenceZoneIndex === -1) {
      return;
    }
    
    const newZone: Zone = {
      id: `zone-${Date.now()}-${Math.random()}`,
      strokes: 10,
      sets: 1,
      spm: MIN_SPM,
    };

    setZones(prevZones => {
      const newZones = [...prevZones];
      newZones.splice(referenceZoneIndex + 1, 0, newZone);
      return newZones;
    });
  }, [zones]);

  const handleDuplicateZone = useCallback((id: string) => {
    if (zones.length >= MAX_ZONES) {
      return;
    }

    const zoneToDuplicateIndex = zones.findIndex(zone => zone.id === id);
    if (zoneToDuplicateIndex === -1) {
      return;
    }
    
    const zoneToDuplicate = zones[zoneToDuplicateIndex];

    const newZone: Zone = {
      ...zoneToDuplicate,
      id: `zone-${Date.now()}-${Math.random()}`,
    };

    setZones(prevZones => {
      const newZones = [...prevZones];
      newZones.splice(zoneToDuplicateIndex + 1, 0, newZone);
      return newZones;
    });
  }, [zones]);
  
  const handleDragStart = useCallback((index: number) => {
    setDraggingIndex(index);
  }, []);

  const handleDragEnter = useCallback((index: number) => {
    if (index !== draggingIndex) {
      setDragOverIndex(index);
    }
  }, [draggingIndex]);

  const handleDragLeave = useCallback(() => {
    setDragOverIndex(null);
  }, []);

  const handleDrop = useCallback(() => {
    if (draggingIndex === null || dragOverIndex === null || draggingIndex === dragOverIndex) {
      return;
    }

    const newZones = [...zones];
    const [draggedItem] = newZones.splice(draggingIndex, 1);
    newZones.splice(dragOverIndex, 0, draggedItem);
    
    setZones(newZones);
  }, [zones, draggingIndex, dragOverIndex]);

  const handleDragEnd = useCallback(() => {
    setDraggingIndex(null);
    setDragOverIndex(null);
  }, []);
  
  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const renderContent = () => {
    if (view === 'connection') {
      return <ConnectionScreen devices={devices} setDevices={setDevices} />;
    }
    
    if (view === 'training') {
      return (
        <>
          <div className="flex justify-center my-8">
            <button
              onClick={handleAddZone}
              disabled={zones.length >= MAX_ZONES}
              aria-label="Add Training Zone"
              title="Add Training Zone"
              className={`flex items-center justify-center w-16 h-16 bg-accent-cyan text-white rounded-2xl shadow-lg hover:bg-accent-cyan-hover transition-all duration-300 transform hover:scale-105 disabled:bg-gray-500 disabled:cursor-not-allowed disabled:scale-100 ${zones.length === 0 ? 'animate-pulse-slow' : ''}`}
            >
              <Icon type="plus" className="w-10 h-10" strokeWidth={2.5} />
            </button>
          </div>
          
           <div className="text-center mb-6 text-lg font-semibold text-white/60">
              <p>{connectedDevicesCount} of 6 devices connected.</p>
           </div>

          {zones.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
              {zones.map((zone, index) => (
                <ZoneBlock
                  key={zone.id}
                  zone={zone}
                  index={index}
                  onUpdate={handleUpdateZone}
                  onRemove={handleRemoveZone}
                  onDuplicate={handleDuplicateZone}
                  onAddAfter={handleAddZoneAfter}
                  isDuplicateDisabled={zones.length >= MAX_ZONES}
                  isLastZone={index === zones.length - 1}
                  // DND Props
                  isDragging={draggingIndex === index}
                  isDragOver={dragOverIndex === index}
                  onDragStart={() => handleDragStart(index)}
                  onDragEnter={() => handleDragEnter(index)}
                  onDragLeave={handleDragLeave}
                  onDragOver={handleDragOver}
                  onDrop={handleDrop}
                  onDragEnd={handleDragEnd}
                />
              ))}
            </div>
          ) : (
            <div className="text-center py-16 px-6 border-2 border-dashed border-white/20 rounded-xl">
              <h2 className="text-3xl font-bold text-accent-cyan">No Training Zones Added</h2>
              <p className="mt-4 text-lg font-semibold text-white/70">
                Click the
                <span
                  className="inline-flex items-center justify-center w-6 h-6 bg-accent-cyan text-white rounded-lg mx-2 align-middle -translate-y-px"
                  aria-label="Add button icon"
                >
                  <Icon type="plus" className="w-4 h-4" strokeWidth={2.5} />
                </span>
                button to start building your training plan.
              </p>
            </div>
          )}
        </>
      );
    }
  };

  return (
    <div className="min-h-screen bg-charcoal bg-cover bg-center p-4 sm:p-6 lg:p-8 pb-28">
      <div className="container mx-auto">
        {renderContent()}
      </div>
      <BottomNavBar currentView={view} onNavigate={setView} />
    </div>
  );
};

export default App;