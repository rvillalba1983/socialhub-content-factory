// Alias cómodos sobre los tipos generados (schema.d.ts). No editar schema.d.ts: se regenera con
//   cd frontend/tools/openapi && npm install && npm run generate
export type {
  Agent, Alert, ArmoryItem, AvailableCommand, Brand, ExternalOperation, ExternalOperationPage, Factory,
  OperationOutcome, Platform, Promotion, Publication, PublicationPage, PublicationStatus, Station,
  StationCommandType, StatusLevel, Task, TaskCommand, TaskCommandType, TaskCounts, TaskStatus, TaskStep,
  AiModel, AppMode, AppModeState, BrandInput, PromotionInput, Milestone, PromotionResource, Automation,
  PublishingRoute, Setting, PieceSpecList, PieceSpec, PieceOrder,
} from './schema';

import type { TaskStep } from './schema';
export type TaskStepState = TaskStep['state'];
