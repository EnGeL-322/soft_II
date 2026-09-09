const fs=require('fs');
const views={calidad:'calidad',rutas:'rutas',liquidaciones:'pagos',quesos:'quesos',zonas:'zonas',trazabilidad:'trazabilidad',reportes:'reportes',parametros:'config','datos-respaldo':'excel'};
for(const [folder,id] of Object.entries(views)){
 const file=`src/app/features/${folder}/${folder}.component.ts`;
 let ts=fs.readFileSync(file,'utf8');
 const match=ts.match(/template: `([\s\S]*?)`,\s*\}\)/);if(!match)continue;
 let html=match[1];
 html=`<section id="${id}" class="view active printable">\n${html}\n</section>`;
 html=html.replaceAll('class="kpi-grid"','class="stats"').replaceAll('class="kpi"','class="stat"');
 html=html.replaceAll('class="tabs"','class="sec-tabs no-print"').replaceAll('class="tab-btn"','class="sec-tab"');
 if(folder==='calidad')html=html.replace('<h1>Control de calidad</h1>','<h1>Calidad e historial</h1>');
 if(folder==='liquidaciones')html=html.replace('<h1>Liquidaciones</h1>','<h1>Reportes de Pago</h1>').replace(/El precio de leche es una tarifa configurable[\s\S]*?antes de producción\./,'Selecciona el proveedor y el periodo para consultar el detalle de pago semanal.');
 if(folder==='reportes')html=html.replace('<h1>Reportes</h1>','<h1>Reportes e historial</h1>');
 if(folder==='trazabilidad')html=html.replace('<h1>Trazabilidad</h1>','<h1>Seguimiento de producción</h1>');
 if(folder==='parametros')html=html.replace('<h1>Parámetros</h1>','<h1>Configuración y seguridad</h1>');
 if(folder==='datos-respaldo')html=html.replace('<h1>Datos y respaldo</h1>','<h1>Excel y respaldo</h1>');
 fs.writeFileSync(`src/app/features/${folder}/${folder}.component.html`,html);
 ts=ts.replace(/template: `[\s\S]*?`,\s*\}\)/,`templateUrl: './${folder}.component.html',\n})`);
 fs.writeFileSync(file,ts);
}
