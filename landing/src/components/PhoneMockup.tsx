import React from 'react'
import { motion } from 'framer-motion'

interface PhoneMockupProps {
  src: string
  alt: string
  className?: string
  showNotch?: boolean
  caption?: string
}

export const PhoneMockup: React.FC<PhoneMockupProps> = ({
  src,
  alt,
  className = '',
  showNotch = true,
  caption,
}) => {
  return (
    <div className={`relative select-none ${className}`}>
      <div className="phone-frame relative mx-auto w-full">
        {/* Side hardware buttons */}
        <div className="pointer-events-none absolute -left-[2.5px] top-[92px] h-8 w-[2.5px] rounded-l bg-neutral-600/70" />
        <div className="pointer-events-none absolute -left-[2.5px] top-[134px] h-12 w-[2.5px] rounded-l bg-neutral-600/70" />
        <div className="pointer-events-none absolute -right-[2.5px] top-[116px] h-14 w-[2.5px] rounded-r bg-neutral-600/70" />

        <div className="phone-screen aspect-[9/19.5] w-full">
          {showNotch && (
            <div className="pointer-events-none absolute left-1/2 top-2 z-20 flex h-[18px] w-[72px] -translate-x-1/2 items-center justify-center rounded-full bg-black/90 ring-1 ring-white/10">
              <div className="h-2 w-2 rounded-full bg-neutral-800 ring-1 ring-neutral-700" />
            </div>
          )}
          <motion.img
            key={src}
            src={src}
            alt={alt}
            initial={{ opacity: 0.4, scale: 1.015 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.35, ease: 'easeOut' }}
            className="h-full w-full object-cover object-top"
            draggable={false}
          />
        </div>
      </div>
      {caption && (
        <p className="mt-3 text-center text-xs font-medium text-[var(--ma-text-muted)]">
          {caption}
        </p>
      )}
    </div>
  )
}
