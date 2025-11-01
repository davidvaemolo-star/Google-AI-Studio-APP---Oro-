
export interface Zone {
  id: string;
  strokes: number;
  sets: number;
  spm: number;
}

export interface HapticDevice {
  id: string;
  name: string;
  status: 'connected' | 'disconnected' | 'connecting';
  seat?: number;
  batteryLevel?: number;
}