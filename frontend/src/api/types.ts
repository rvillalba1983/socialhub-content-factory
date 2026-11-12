// Alias cómodos sobre los tipos generados (schema.d.ts). No editar schema.d.ts: se regenera con
//   cd frontend/tools/openapi && npm install && npm run generate
export type {
  Agent, Alert, ArmoryItem, AvailableCommand, Brand, ExternalOperation, ExternalOperationPage, Factory,
  OperationOutcome, Platform, Promotion, Publication, PublicationPage, PublicationStatus, Station,
  StationCommandType, StatusLevel, Task, TaskCommand, TaskCommandType, TaskCounts, TaskStatus, TaskStep,
  AiModel, AppMode, AppModeState, BrandInput, PromotionInput, Milestone, PromotionResource, Automation,
  PublishingRoute, Setting, PieceSpecList, PieceSpec, PieceOrder, Folder, FolderList, FolderInput, FolderRole,
  PublicationInput, PublicationFile, PublicationFileList, AiRequestType, AiSettings, AiSettingsInput, AiRequest,
  AiRequestPage, AiOrderInput, AiOrder, BrandIdentity, QualityIssue, QualityReport,
  RejectionReason, EditorialDecision, EditorialMemory, PublishJob, PublishRouteState,
  PublicationMetrics, ExternalCalendar, ExternalPost, ExternalSource, PubloraStatus, PubloraConnection, PubloraAccount, PubloraAccountList, PubloraAccountInput,
  InstagramStatus, InstagramCheck, InstagramBrandCheck, InstagramAccount,
  Backup, BackupList, BackupFolderInput, Restoration, RestorationInput,
} from './schema';

import type { TaskStep } from './schema';
export type TaskStepState = TaskStep['state'];
