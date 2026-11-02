package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.model.BrandDto;
import com.socialhub.api.model.BrandInputDto;
import com.socialhub.api.model.FolderDto;
import com.socialhub.api.model.FolderInputDto;
import com.socialhub.api.model.FolderListDto;
import com.socialhub.api.model.FolderListInputDto;
import com.socialhub.api.model.FolderRoleDto;
import com.socialhub.api.model.PromotionDto;
import com.socialhub.api.model.PromotionInputDto;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.FolderRole;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface PromotionWebMapper {

    BrandDto toDto(Brand brand);

    List<BrandDto> toBrandDtos(List<Brand> brands);

    PromotionDto toDto(Promotion promotion);

    List<PromotionDto> toPromotionDtos(List<Promotion> promotions);

    BrandDraft toDraft(BrandInputDto input);

    PromotionDraft toDraft(PromotionInputDto input);

    FolderRole toDomain(FolderRoleDto role);

    FolderRoleDto toDto(FolderRole role);

    default FolderListDto toFolderList(List<FolderAssignment> folders) {
        return new FolderListDto(folders.stream()
                .map(f -> new FolderDto(toDto(f.role()), f.location(), FolderDto.OriginEnum.valueOf(f.origin().name()),
                        available(f.location())))
                .toList());
    }

    /** Un papel solo puede aparecer una vez. */
    default Map<FolderRole, String> toFolderMap(FolderListInputDto input) {
        Map<FolderRole, String> map = new EnumMap<>(FolderRole.class);
        for (FolderInputDto item : input.getItems()) {
            FolderRole role = toDomain(item.getRole());
            if (map.put(role, item.getLocation()) != null) {
                throw new IllegalArgumentException("El papel «" + role.label() + "» aparece dos veces");
            }
        }
        return map;
    }

    private static boolean available(String location) {
        try {
            return Files.isDirectory(Path.of(location));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
