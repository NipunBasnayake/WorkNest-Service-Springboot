package com.worknest.master.service.impl;

import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.service.PlatformTenantService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final PlatformTenantRepository tenantRepository;
    private final ModelMapper modelMapper;

    public PlatformTenantServiceImpl(
            PlatformTenantRepository tenantRepository,
            ModelMapper modelMapper) {
        this.tenantRepository = tenantRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public List<PlatformTenantResponseDto> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PlatformTenantResponseDto getTenantByKey(String tenantKey) {
        PlatformTenant tenant = tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with key: " + tenantKey));
        return mapToResponseDto(tenant);
    }

    @Override
    public PlatformTenantResponseDto getTenantById(Long id) {
        PlatformTenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + id));
        return mapToResponseDto(tenant);
    }

    @Override
    public boolean tenantExists(String tenantKey) {
        return tenantRepository.existsByTenantKey(tenantKey);
    }

    private PlatformTenantResponseDto mapToResponseDto(PlatformTenant tenant) {
        return modelMapper.map(tenant, PlatformTenantResponseDto.class);
    }
}

