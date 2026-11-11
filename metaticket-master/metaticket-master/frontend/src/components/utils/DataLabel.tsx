import { ReactNode } from 'react';

const DataLabel = ({
  label,
  value,
  className,
}: {
  label: string
  value: string
  className?: string
}) => {

  function isValidURL(string: string) {
    try { 
      return Boolean(new URL(string)); 
    }
    catch(e){ 
      return false; 
    }
  };

  const openDataCollectorWindow = () => {

  }

  return (
    <div className={className}>
      <div>{label}</div>

      {isValidURL(value) ? (
        <div
          className="content-center flex block w-full"
        >
          <a href={value} target="_blank," className='grow block truncate font-bold'>
            {value}
          </a>
          <div className="ml-1 text-sm font-medium cursor-pointer" onClick={() => navigator.clipboard.writeText(value)}>
              Copy
          </div>
            
        </div>
      ) : (
        <div className="font-bold mt-1">{value == "true" ? "Có" : value == "false" ? "Không" : value}</div>
      )}
      
    </div>
  )
}

export { DataLabel }
