import React from 'react';
import Icon from './Icon';

interface BottomNavBarProps {
  currentView: 'connection' | 'training';
  onNavigate: (view: 'connection' | 'training') => void;
}

interface NavItemProps {
  label: string;
  icon: 'bluetooth' | 'training';
  isActive: boolean;
  onClick: () => void;
}

const NavItem: React.FC<NavItemProps> = ({ label, icon, isActive, onClick }) => {
  const activeClasses = 'text-accent-cyan';
  const inactiveClasses = 'text-white/70 hover:text-white';

  return (
    <button
      onClick={onClick}
      className={`flex flex-col items-center justify-center w-full pt-2 pb-1 transition-colors duration-200 ${isActive ? activeClasses : inactiveClasses}`}
      aria-current={isActive ? 'page' : undefined}
    >
      <Icon type={icon} className="w-7 h-7 mb-1" strokeWidth={2} />
      <span className="text-xs font-bold tracking-wider uppercase">{label}</span>
    </button>
  );
};

export const BottomNavBar: React.FC<BottomNavBarProps> = ({ currentView, onNavigate }) => {
  return (
    <nav className="fixed bottom-0 left-0 right-0 h-20 bg-black/30 backdrop-blur-sm border-t border-white/20 shadow-lg z-50">
      <div className="container mx-auto h-full flex justify-around items-center">
        <NavItem 
          label="Devices"
          icon="bluetooth"
          isActive={currentView === 'connection'}
          onClick={() => onNavigate('connection')}
        />
        <NavItem
          label="Training"
          icon="training"
          isActive={currentView === 'training'}
          onClick={() => onNavigate('training')}
        />
      </div>
    </nav>
  );
};