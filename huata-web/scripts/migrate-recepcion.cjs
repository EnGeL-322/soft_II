const fs=require('fs');
const p='src/app/features/recojo/recojo.component.ts';let ts=fs.readFileSync(p,'utf8');let html=ts.match(/template: `([\s\S]*?)`,\s*\}\)/)[1];
html='<section id="recepcion" class="view active printable">'+html+'</section>';
html=html.replace('<div class="kpi-grid">','<div class="stats">').replaceAll('<div class="kpi">','<div class="stat">');
html=html.replace('<span>Recepciones hoy</span>\n        <strong>{{ recepcionesHoy() }}</strong>\n        <small class="muted">{{ hoyTexto }}</small>','<span>Envases de referencia</span><strong>22 / 35 L</strong><small class="muted">Seleccionables.</small>');
html=html.replace(/<span>Litros hoy<\/span>[\s\S]*?<small class="muted">Sobre esta página<\/small>/,'<span>Horario de acopio</span><strong>08:40–10:00</strong><small class="muted">Furgón / cierre de referencia.</small>');
html=html.replace(/<div class="note" style="margin-bottom:16px">[\s\S]*?<\/div>/,'<div class="mobile-app-pointer-v141 no-print"><b>App del acopiador:</b> los recojos enviados desde la aplicación móvil se muestran en el registro para su validación.</div>');
html=html.replace('<form class="card" style="margin-bottom:20px" (ngSubmit)="registrar()">','<div class="prototype-modal" role="dialog" aria-modal="true" aria-label="Nueva recepción"><div class="prototype-modal-box"><form class="card" (ngSubmit)="registrar()">').replace('      </form>\n    }','      </form></div></div>\n    }');
html=html.replace('(click)="abrirFormulario()">+ Nueva recepción</button>', '(click)="abrirFormulario()">+ Nueva recepción</button><button type="button" class="btn btn-blue" (click)="exportar()">Exportar Excel</button>');
fs.writeFileSync('src/app/features/recojo/recojo.component.html',html);
ts=ts.replace(/template: `[\s\S]*?`,\s*\}\)/,"templateUrl: './recojo.component.html',\n})");
ts="import { ExportacionesService } from '../../core/services/exportaciones.service';\n"+ts;
ts=ts.replace('private readonly route: ActivatedRoute,','private readonly route: ActivatedRoute,\n    private readonly exportaciones: ExportacionesService,');
ts=ts.replace('export class RecojoComponent implements OnInit, OnDestroy {',`export class RecojoComponent implements OnInit, OnDestroy {
  exportar() { this.exportaciones.descargar('Recepciones').subscribe(blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='Huata_Recepciones.xlsx';a.click();URL.revokeObjectURL(url);}); }
`);
fs.writeFileSync(p,ts);
