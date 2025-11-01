import React, { useState, useMemo, useCallback, useRef } from 'react';
import type { HapticDevice } from '../types';
import Icon from './Icon';

const DEVICE_NAMES = [
  'Kai', 'Moana', 'Nalu', 'Koa', 'Hoku', 'Makai', 'Aukai', 'Lani',
  'Triton', 'Poseidon', 'Mako', 'Stingray', 'Barracuda', 'Marlin',
  'Voyager', 'Navigator', 'Compass', 'Anchor', 'Tsunami', 'Coral'
];

const shuffleArray = (array: string[]) => {
  const newArr = [...array];
  for (let i = newArr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [newArr[i], newArr[j]] = [newArr[j], newArr[i]];
  }
  return newArr;
};

interface ConnectionScreenProps {
  devices: HapticDevice[];
  setDevices: React.Dispatch<React.SetStateAction<HapticDevice[]>>;
}

const statusConfig = {
  disconnected: { text: 'Disconnected', color: 'bg-white/30', button: 'Connect' },
  connecting: { text: 'Connecting...', color: 'bg-yellow-500 animate-pulse', button: 'Connecting' },
  connected: { text: 'Connected', color: 'bg-zone-green-end', button: 'Disconnect' },
};

const getBatteryColor = (level: number) => {
  if (level > 50) return 'text-zone-green-end';
  if (level > 20) return 'text-yellow-500';
  return 'text-red-600';
};

interface DeviceCardProps {
  device: HapticDevice;
  onAction: (id: string) => void;
  // DND props
  onDragStart: (e: React.DragEvent) => void;
  onDragEnter: () => void;
  onDragLeave: () => void;
  onDragOver: (e: React.DragEvent<HTMLDivElement>) => void;
  onDrop: () => void;
  onDragEnd: () => void;
  isDraggable: boolean;
  isDragging: boolean;
  isDragOver: boolean;
  // Seat info
  seatNumber?: number;
  seatRole?: 'Pacer' | 'Steerer';
}

// A unified device card for both connected and disconnected devices
const DeviceCard: React.FC<DeviceCardProps> = ({ 
  device, onAction, onDragStart, onDragEnter, onDragLeave, onDragOver, onDrop, onDragEnd, 
  isDraggable, isDragging, isDragOver, seatNumber, seatRole 
}) => {
  const divRef = useRef<HTMLDivElement>(null);

  const internalDragLeaveHandler = (e: React.DragEvent<HTMLDivElement>) => {
    // This check prevents the drag leave event from firing when the cursor moves over child elements.
    if (divRef.current && !divRef.current.contains(e.relatedTarget as Node)) {
      onDragLeave();
    }
  };
  
  const config = statusConfig[device.status];
  const draggingStyles = isDragging ? 'opacity-40 scale-95' : 'opacity-100 scale-100';
  const dragOverStyles = isDragOver ? 'ring-2 ring-accent-cyan ring-offset-2 ring-offset-charcoal' : '';
  const draggableClasses = isDraggable ? 'cursor-grab active:cursor-grabbing active:scale-95' : '';

  return (
    <div
      ref={divRef}
      draggable={isDraggable}
      onDragStart={onDragStart}
      onDragEnter={onDragEnter}
      onDragLeave={internalDragLeaveHandler}
      onDragOver={onDragOver}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
      className={`relative bg-white/10 rounded-xl p-4 text-left flex items-center justify-between shadow-md transition-all duration-300 ${draggableClasses} ${draggingStyles} ${dragOverStyles}`}
    >
      <div className="flex items-center gap-4 flex-grow">
        {seatNumber && (
          <div className="flex-shrink-0 flex flex-col items-center justify-center p-2 rounded-lg bg-charcoal w-16 h-16 text-center">
            <span className="text-3xl font-bold text-accent-cyan">{seatNumber}</span>
            {seatRole && <span className="text-xs font-bold uppercase tracking-wider text-accent-cyan leading-tight">{seatRole}</span>}
          </div>
        )}
        <div className="flex-grow">
          <h3 className="text-2xl font-bold text-white">{device.name}</h3>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <div className="flex items-center gap-2">
              <span className={`w-3 h-3 rounded-full ${config.color}`}></span>
              <p className="text-white/80 font-semibold">{config.text}</p>
            </div>
            {device.status === 'connected' && typeof device.batteryLevel === 'number' && (
              <div className={`flex items-center gap-1 text-sm font-semibold ${getBatteryColor(device.batteryLevel)}`}>
                <Icon type="battery" className="w-5 h-5" />
                <span>{device.batteryLevel}%</span>
              </div>
            )}
          </div>
        </div>
      </div>
      
      <div className="flex-shrink-0 ml-4">
        <button
          onClick={() => onAction(device.id)}
          disabled={device.status === 'connecting'}
          className={`font-bold py-2 px-4 rounded-md transition-colors duration-200 text-lg w-32 text-center ${
            device.status === 'connected' 
            ? 'bg-red-600 hover:bg-red-700' 
            : 'bg-accent-cyan hover:bg-accent-cyan-hover'
          } disabled:bg-gray-500 disabled:cursor-not-allowed`}
        >
          {config.button}
        </button>
      </div>
    </div>
  )
}


export const ConnectionScreen: React.FC<ConnectionScreenProps> = ({ devices, setDevices }) => {
  const [isScanning, setIsScanning] = useState(false);
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);

  const { connectedDevices, otherDevices } = useMemo(() => {
    const connected = devices
      .filter(d => d.status === 'connected')
      .sort((a, b) => (a.seat || 999) - (b.seat || 999)); // Sort by seat, unassigned last
    
    const connectedIds = new Set(connected.map(d => d.id));
    const others = devices.filter(d => !connectedIds.has(d.id));
    
    return { connectedDevices: connected, otherDevices: others };
  }, [devices]);


  const handleScan = () => {
    setIsScanning(true);
    setDevices([]);

    // Simulate scanning for devices
    setTimeout(() => {
      const shuffledNames = shuffleArray(DEVICE_NAMES);
      const foundDevices: HapticDevice[] = Array.from({ length: 6 }, (_, i) => ({
        id: `pacer-${i + 1}`,
        name: shuffledNames[i] || `Device ${i + 1}`,
        status: 'disconnected',
      }));
      setDevices(foundDevices);
      setIsScanning(false);
    }, 2000);
  };

  const handleDeviceAction = (id: string) => {
    const device = devices.find(d => d.id === id);
    if (!device) return;

    if (device.status === 'connected') {
      setDevices(prev => prev.map(d => d.id === id ? { ...d, status: 'disconnected', seat: undefined, batteryLevel: undefined } : d));
    } else if (device.status === 'disconnected') {
      setDevices(prev => prev.map(d => d.id === id ? { ...d, status: 'connecting' } : d));
      setTimeout(() => {
        const batteryLevel = Math.floor(Math.random() * 81) + 20; // Simulate 20-100%
        setDevices(prev => prev.map(d => d.id === id ? { ...d, status: 'connected', batteryLevel } : d));
      }, 1500);
    }
  };
  
  // Drag and Drop Handlers
  const handleDragStart = useCallback((e: React.DragEvent, index: number) => {
    e.dataTransfer.effectAllowed = 'move';
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

    const reorderedConnected = [...connectedDevices];
    const [draggedItem] = reorderedConnected.splice(draggingIndex, 1);
    reorderedConnected.splice(dragOverIndex, 0, draggedItem);
    
    const updatedConnectedWithSeats = reorderedConnected.map((device, index) => ({
      ...device,
      seat: index + 1,
    }));

    const connectedIds = new Set(updatedConnectedWithSeats.map(d => d.id));
    const allOtherDevices = devices.filter(d => !connectedIds.has(d.id));
    
    setDevices([...updatedConnectedWithSeats, ...allOtherDevices]);

  }, [connectedDevices, draggingIndex, dragOverIndex, devices, setDevices]);

  const handleDragEnd = useCallback(() => {
    setDraggingIndex(null);
    setDragOverIndex(null);
  }, []);
  
  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  return (
    <div>
      <div className="text-center max-w-4xl mx-auto pt-8">
        <h2 className="text-4xl font-bold text-accent-cyan mb-2">Connect Oro Devices</h2>
        <p className="text-lg text-white/70 mb-8">Turn on your devices and place them nearby before scanning.</p>

        <div className="mb-10">
          <button
            onClick={handleScan}
            disabled={isScanning}
            className={`flex items-center justify-center gap-3 w-48 mx-auto bg-accent-cyan text-white font-bold py-4 px-6 rounded-lg text-xl uppercase tracking-wider shadow-lg hover:bg-accent-cyan-hover transition-all duration-300 transform hover:scale-105 disabled:bg-gray-500 disabled:cursor-not-allowed disabled:scale-100 ${devices.length === 0 && !isScanning ? 'animate-pulse-slow' : ''}`}
          >
            {isScanning ? (
              <>
                <Icon type="spinner" className="w-7 h-7 animate-spin" strokeWidth={2.5} />
                Scanning...
              </>
            ) : (
              <>
                <Icon type="scan" className="w-7 h-7" strokeWidth={2} />
                Scan
              </>
            )}
          </button>
        </div>

        {devices.length > 0 && (
          <div className="text-left">
            {/* Seat Assignment Area */}
            <div className="mb-12">
              <h3 className="text-2xl font-bold text-accent-cyan mb-3">Assign Seats</h3>
              <div className="bg-charcoal p-4 rounded-lg mb-5 border border-white/10">
                <div className="flex justify-center items-center text-white/80 space-x-4">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-8 h-8">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M3 7.5 7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5" />
                    </svg>
                  <div className="text-lg font-semibold uppercase tracking-wider">Drag to Order</div>
                </div>
              </div>
              
              {connectedDevices.length > 0 ? (
                <div className="space-y-3">
                  {connectedDevices.map((device, index) => {
                    let seatRole: 'Pacer' | 'Steerer' | undefined = undefined;
                    if (index === 0) seatRole = 'Pacer';
                    if (index === connectedDevices.length - 1 && connectedDevices.length > 1) seatRole = 'Steerer';

                    return (
                      <DeviceCard 
                        key={device.id} 
                        device={device} 
                        onAction={handleDeviceAction}
                        onDragStart={(e) => handleDragStart(e, index)}
                        onDragEnter={() => handleDragEnter(index)}
                        onDragLeave={handleDragLeave}
                        onDragOver={handleDragOver}
                        onDrop={handleDrop}
                        onDragEnd={handleDragEnd}
                        isDraggable={true}
                        isDragging={draggingIndex === index}
                        isDragOver={dragOverIndex === index}
                        seatNumber={index + 1}
                        seatRole={seatRole}
                      />
                    );
                  })}
                </div>
              ) : (
                <div className="text-center py-10 px-6 border-2 border-dashed border-white/20 rounded-xl">
                  <p className="text-lg font-semibold text-white/70">Connect devices to assign seats.</p>
                </div>
              )}
            </div>

            {otherDevices.length > 0 && (
              <div className="mt-8">
                <h3 className="text-xl font-bold text-white mb-4">Other Devices ({otherDevices.length})</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {otherDevices.map(device => (
                    <DeviceCard 
                      key={device.id} 
                      device={device} 
                      onAction={handleDeviceAction}
                      onDragStart={() => {}}
                      onDragEnter={() => {}}
                      onDragLeave={() => {}}
                      onDragOver={handleDragOver}
                      onDrop={() => {}}
                      onDragEnd={() => {}}
                      isDraggable={false}
                      isDragging={false}
                      isDragOver={false}
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};