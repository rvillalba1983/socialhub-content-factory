// Alias cómodos sobre los tipos generados (schema.d.ts). No editar schema.d.ts: se regenera con
//   cd frontend/tools/openapi && npm install && npm run generate
export type {
  Agent, Alert, ArmoryItem, AvailableCommand, Brand, ExternalOperation, ExternalOperationPage, Factory,
  OperationOutcome, Platform, Promotion, Publication, PublicationPage, PublicationStatus, Station,
  StationCommandType, StatusLevel, Task, TaskCommand, TaskCommandType, TaskCounts, TaskStatus, TaskStep,
  AiModel, AppMode, AppModeState, AppVersion, Autostart, UpdateStatus, LogsFolder, BrandInput, PromotionInput, Milestone, PromotionResource, Automation,
  PublishingRoute, Setting, PieceSpecList, PieceSpec, PieceOrder, Folder, FolderList, FolderInput, FolderRole,
  PublicationInput, PublicationSound, PublicationSoundInput, SoundMode, PublicationFile, PublicationFileList, AiRequestType, AiSettings, AiSettingsInput, PublishSettings, PublishSettingsInput, AudioSettings, AudioSettingsInput, AiRequest,
  AiRequestPage, AiOrderInput, AiOrder, BrandIdentity, QualityIssue, QualityReport,
  RejectionReason, EditorialDecision, EditorialMemory, PublishJob, PublishRouteState,
  PublicationOutcome, SlotProposal, DuplicateRef, PublicationMetrics, PromotionMetrics, NetworkMetrics, FormatMetrics, ExternalCalendar, ExternalPost, ExternalSource, PubloraStatus, PubloraConnection, PubloraAccount, PubloraAccountList, PubloraAccountInput, PublerAccount, PublerAccountList, PublerAccountInput, PublerMoveInput, PublerMoveResult, AlertAction,
  InstagramStatus, InstagramCheck, InstagramBrandCheck, InstagramAccount, InstagramConnection, InstagramAppInput,
  InstagramOAuthStart, InstagramRenewal, InstagramDetectedAccount, InstagramDetectedAccountList,
  BrandKitView, BrandKitVersion, BrandKitColor, BrandKitTemplate, BrandKitRequestInput, BrandKitRejectInput, BrandKitRejection,
  Backup, BackupList, BackupFolderInput, Restoration, RestorationInput,
} from './schema';

import type { TaskStep } from './schema';
export type TaskStepState = TaskStep['state'];
